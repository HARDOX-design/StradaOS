#!/usr/bin/env python3
"""Revisa lo que StradaOS le pidió al ELM327 simulado en cada fase (bloque21.sh). Sale con 1 si algo falla.

Uso: elmcheck.py LOG [archivo de resumen]
El guion marca el inicio de cada fase con una línea {"c": "#FASE <nombre>"}.
"""
import json
import sys


def pids(c):
    """PIDs de una consulta del modo 01 y si trae el número de tramas al final."""
    body = c[2:]
    counted = len(body) % 2 == 1
    if counted:
        body = body[:-1]
    return [body[i:i + 2] for i in range(0, len(body), 2)], counted


def main():
    rows = [json.loads(l) for l in open(sys.argv[1]) if l.strip()]
    phases, cur = {}, None
    for r in rows:
        if r["c"].startswith("#FASE "):
            cur = r["c"][6:]
            phases[cur] = []
            continue
        if cur:
            phases[cur].append(r)
    out, ok = [], True

    def check(name, cond, detail):
        nonlocal ok
        out.append(("OK   " if cond else "FALLA") + " · " + name + " · " + detail)
        ok = ok and cond

    def q01(rs):
        return [r for r in rs if r["c"].startswith("01") and len(r["c"]) > 2]

    # 1. lectura agrupada con el número de tramas
    rs = phases.get("multi", [])
    if rs:
        t0 = rs[0]["t"] + 15
        win = [r for r in q01(rs) if r["t"] >= t0]
        grp = [r for r in win if len(pids(r["c"])[0]) > 1]
        cnt = [r for r in grp if pids(r["c"])[1]]
        span = (win[-1]["t"] - win[0]["t"]) if len(win) > 1 else 0
        rate = len(win) / span if span else 0
        check("multi", len(grp) >= 20 and len(cnt) >= 1,
              "%d consultas agrupadas (%d con número de tramas), %.1f consultas/s" % (len(grp), len(cnt), rate))
        odo = [r for r in rs if r["c"] == "01A6"]
        check("odómetro pedido", len(odo) >= 1, "%d veces 01A6" % len(odo))
    else:
        check("multi", False, "sin datos")

    # 2. respuestas perdidas: se recupera sin reconectar
    rs = phases.get("lose", [])
    ati = [r for r in rs if r["c"] == "ATI"]
    atz = [r for r in rs if r["c"] == "ATZ"]
    tail = [r for r in q01(rs) if rs and r["t"] >= rs[-1]["t"] - 10]
    check("lose", len(ati) >= 1 and len(atz) == 0 and len(tail) >= 10,
          "%d «ATI» de rescate, %d reconexiones, %d consultas en los últimos 10 s" % (len(ati), len(atz), len(tail)))

    # 3. clon que no entiende el número de tramas: deja de mandarlo y sigue agrupando
    rs = q01(phases.get("nocount", []))
    first = next((i for i, r in enumerate(rs) if pids(r["c"])[1]), None)
    after = rs[first + 1:] if first is not None else rs
    late_cnt = [r for r in after if pids(r["c"])[1]]
    late_grp = [r for r in after if len(pids(r["c"])[0]) > 1]
    check("nocount", len(late_cnt) <= 1 and len(late_grp) >= 5,
          "%d con número de tramas después del «?», %d agrupadas" % (len(late_cnt), len(late_grp)))

    # 4. escáner que no entiende varios PIDs: vuelve a la lectura de a uno
    rs = phases.get("nomulti", [])
    tail = [r for r in q01(rs) if rs and r["t"] >= rs[-1]["t"] - 10]
    grp = [r for r in tail if len(pids(r["c"])[0]) > 1]
    check("nomulti", len(grp) == 0 and len(tail) >= 8,
          "%d agrupadas y %d sueltas en los últimos 10 s" % (len(grp), len(tail) - len(grp)))

    # 5. auto sin odómetro: reconecta y no lo pide
    rs = phases.get("noodo", [])
    atz = [r for r in rs if r["c"] == "ATZ"]
    odo = [r for r in rs if r["c"] == "01A6"]
    check("noodo", len(atz) >= 1 and len(odo) == 0, "%d conexiones, %d veces 01A6" % (len(atz), len(odo)))

    text = "\n".join(out)
    print(text)
    if len(sys.argv) > 2:
        with open(sys.argv[2], "w") as f:
            f.write(text + "\n")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
