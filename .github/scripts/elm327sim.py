#!/usr/bin/env python3
"""ELM327 Wi-Fi simulado para las pruebas del escáner OBD (Bloque 21, 2.34).

Escucha en TCP (por defecto 0.0.0.0:35000, como los ELM327 Wi-Fi). Desde el emulador se llega con
«tcp:10.0.2.2:35000». Se comporta como un clon v1.5 con un auto CAN 11 bits/500 kbps.

Modo (se lee en cada comando del archivo MODE_FILE, así el guion lo cambia sin reconectar):
  multi    entiende varios PIDs por consulta y el número de tramas al final
  nocount  contesta «?» cuando la consulta trae el número de tramas (clon que no lo entiende)
  nomulti  contesta «?» a cualquier consulta con más de un PID
  lose     como multi, pero se «traga» una de cada 15 respuestas (no manda nada, ni el «>»)
  noodo    como multi, pero el auto no tiene odómetro (PID A6); se aplica al conectar (lee los PIDs disponibles)

Cada comando recibido queda en LOG_FILE como una línea JSON: {"t": segundos, "c": comando, "m": modo}.
La velocidad sube y baja entre 0 y 80 km/h (o fija con SPEED_FILE); el odómetro suma lo recorrido.
"""
import json
import os
import socket
import sys
import threading
import time

HOST = os.environ.get("ELM_HOST", "0.0.0.0")
PORT = int(os.environ.get("ELM_PORT", "35000"))
MODE_FILE = os.environ.get("ELM_MODE", "/tmp/elm-mode")
SPEED_FILE = os.environ.get("ELM_SPEED", "/tmp/elm-speed")
LOG_FILE = os.environ.get("ELM_LOG", "/tmp/elm-log.jsonl")
ODO0 = 45678.9            # km al empezar

T0 = time.time()
lock = threading.Lock()
odo = [ODO0, None, 0.0]   # km, última hora, última velocidad


def mode():
    try:
        with open(MODE_FILE) as f:
            return f.read().strip() or "multi"
    except OSError:
        return "multi"


def speed():
    try:
        with open(SPEED_FILE) as f:
            return float(f.read().strip())
    except (OSError, ValueError):
        t = time.time() - T0
        ph = t % 60
        return ph * 80 / 30 if ph < 30 else (60 - ph) * 80 / 30


def odometer(v):
    now = time.time()
    with lock:
        if odo[1] is not None:
            odo[0] += (odo[2] + v) / 2 * (now - odo[1]) / 3600
        odo[1] = now
        odo[2] = v
        return odo[0]


def bitmap(pids, base):
    """4 bytes con los PIDs disponibles entre base+1 y base+0x20."""
    v = 0
    for p in pids:
        if base < p <= base + 0x20:
            v |= 1 << (32 - (p - base))
    return "%08X" % v


def supported(m):
    s = {0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x14, 0x1F, 0x20,
         0x21, 0x2F, 0x31, 0x33, 0x40, 0x42, 0x46, 0x49, 0x5C}
    if m != "noodo":
        s |= {0x60, 0x80, 0xA0, 0xA6}
    return s


def data(pid):
    """Bytes de datos (hex) de un PID, o None si el auto no lo tiene."""
    t = time.time() - T0
    v = speed()
    rpm = 800 + v * 30
    if pid == 0x04: return "%02X" % int(30 * 255 / 100)
    if pid == 0x05: return "%02X" % (88 + 40)
    if pid == 0x06: return "%02X" % 130
    if pid == 0x07: return "%02X" % 126
    if pid == 0x0B: return "%02X" % 45
    if pid == 0x0C: return "%04X" % int(rpm * 4)
    if pid == 0x0D: return "%02X" % int(round(v))
    if pid == 0x0E: return "%02X" % ((10 + 64) * 2)
    if pid == 0x0F: return "%02X" % (30 + 40)
    if pid == 0x10: return "%04X" % 450
    if pid == 0x11: return "%02X" % 40
    if pid == 0x14: return "%02X%02X" % (int(0.45 * 200 + 0.3 * 200 * ((int(t * 3)) % 2)), 0xFF)
    if pid == 0x1F: return "%04X" % int(t)
    if pid == 0x21: return "0000"
    if pid == 0x2F: return "%02X" % int(60 * 255 / 100)
    if pid == 0x31: return "%04X" % 1200
    if pid == 0x33: return "%02X" % 85
    if pid == 0x42: return "%04X" % 14100
    if pid == 0x46: return "%02X" % (28 + 40)
    if pid == 0x49: return "%02X" % 20
    if pid == 0x5C: return "%02X" % (92 + 40)
    if pid == 0xA6: return "%08X" % int(odometer(v) * 10)
    return None


def frames(payload):
    """Respuesta CAN sin encabezados ni espacios: una línea si cabe en una trama, si no ISO-TP («00E», «0:…»)."""
    n = len(payload) // 2
    if n <= 7:
        return [payload]
    out = ["%03X" % n, "0:" + payload[:12]]
    rest, i = payload[12:], 1
    while rest:
        chunk = rest[:14]
        rest = rest[14:]
        out.append("%X:" % (i & 0xF) + chunk + "00" * ((14 - len(chunk)) // 2))
        i += 1
    return out


def answer(c, m, counter):
    if c.startswith("AT"):
        if c in ("ATZ", "ATI", "ATWS"): return ["ELM327 v1.5"]
        if c == "ATDPN": return ["A6"]
        if c == "ATDP": return ["AUTO, ISO 15765-4 (CAN 11/500)"]
        if c == "ATRV": return ["12.6V"]
        return ["OK"]
    if c in ("03", "07", "0A"):
        return ["4%s00" % c[1]]
    if not c.startswith("01") or len(c) < 4:
        return ["NO DATA"]
    body = c[2:]
    count = None
    if len(body) % 2 == 1:
        count, body = body[-1], body[:-1]
    pids = [int(body[i:i + 2], 16) for i in range(0, len(body), 2)]
    if len(pids) > 6:
        return ["?"]
    if count is not None and m == "nocount":
        return ["?"]
    if len(pids) > 1 and m == "nomulti":
        return ["?"]
    if m == "lose" and counter % 15 == 14:
        return None                                   # respuesta perdida: ni el «>»
    sup = supported(m)
    out = "41"
    for p in pids:
        if p % 0x20 == 0 and p <= 0xC0:
            if p == 0 or p in sup:
                out += "%02X" % p + bitmap(sup, p)
            continue
        d = data(p) if p in sup else None
        if d is not None:
            out += "%02X" % p + d
    if out == "41":
        return ["NO DATA"]
    return frames(out)


def log(c, m):
    with open(LOG_FILE, "a") as f:
        f.write(json.dumps({"t": round(time.time() - T0, 3), "c": c, "m": m}) + "\n")


def client(conn):
    buf = b""
    counter = 0
    conn.settimeout(None)
    with conn:
        while True:
            try:
                ch = conn.recv(256)
            except OSError:
                return
            if not ch:
                return
            buf += ch
            while b"\r" in buf:
                line, buf = buf.split(b"\r", 1)
                c = line.decode("ascii", "replace").strip().upper().replace(" ", "")
                if not c:
                    continue
                m = mode()
                log(c, m)
                r = answer(c, m, counter)
                if c.startswith("01"):
                    counter += 1
                if r is None:
                    continue
                time.sleep(0.03 if c.startswith("01") else 0.005)
                try:
                    conn.sendall(("\r".join(r) + "\r\r>").encode("ascii"))
                except OSError:
                    return


def main():
    open(LOG_FILE, "w").close()
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((HOST, PORT))
    s.listen(4)
    print("ELM327 simulado en %s:%d" % (HOST, PORT), flush=True)
    while True:
        conn, addr = s.accept()
        log("CONECTA %s" % addr[0], mode())
        threading.Thread(target=client, args=(conn,), daemon=True).start()


if __name__ == "__main__":
    sys.exit(main())
