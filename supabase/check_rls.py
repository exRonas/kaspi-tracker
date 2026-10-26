"""Live check that the Supabase project only lets partners see each other.

Creates three anonymous users (A and B pair up, C is a stranger) and asserts
what each of them can and cannot do. Run after applying schema.sql:

    SUPABASE_URL=https://xxxx.supabase.co SUPABASE_ANON_KEY=eyJ... python supabase/check_rls.py

or with the keys in local.properties (supabase.url / supabase.anonKey) and no
environment variables. Leaves three anonymous users behind in auth.users.
"""
import json
import os
import pathlib
import urllib.error
import urllib.request
import uuid


def load_keys():
    url = os.environ.get("SUPABASE_URL")
    key = os.environ.get("SUPABASE_ANON_KEY")
    if url and key:
        return url.rstrip("/"), key
    props = pathlib.Path(__file__).resolve().parent.parent / "local.properties"
    values = {}
    for line in props.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            values[k.strip()] = v.strip()
    return values["supabase.url"].rstrip("/"), values["supabase.anonKey"]


URL, ANON = load_keys()


def call(method, path, token=None, body=None, prefer=None):
    """Returns (status, parsed JSON or text)."""
    headers = {"apikey": ANON, "Content-Type": "application/json"}
    headers["Authorization"] = f"Bearer {token or ANON}"
    if prefer:
        headers["Prefer"] = prefer
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(URL + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request) as response:
            raw = response.read().decode()
            status = response.status
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        status = error.code
    try:
        return status, json.loads(raw) if raw else None
    except json.JSONDecodeError:
        return status, raw


def sign_up():
    status, body = call("POST", "/auth/v1/signup", body={"data": {}})
    assert status == 200, f"anonymous sign-in failed ({status}): {body} — is it enabled?"
    return body["access_token"], body["user"]["id"]


def rpc(token, name, args=None):
    return call("POST", f"/rest/v1/rpc/{name}", token, args or {})


def rows(token, query):
    status, body = call("GET", f"/rest/v1/{query}", token)
    assert status == 200, f"select {query} failed ({status}): {body}"
    return body


def tx_row(owner_hint):
    return {
        "id": str(uuid.uuid4()), "timestamp": 1, "amount": 100.0, "type": "PURCHASE",
        "merchant": f"test {owner_hint}", "source": "MANUAL",
    }


def main():
    a, a_id = sign_up()
    b, b_id = sign_up()
    c, c_id = sign_up()

    # 1. A gets a code.
    status, code = rpc(a, "create_pair_code")
    assert status == 200, code
    alphabet = set("ABCDEFGHJKMNPQRSTUVWXYZ23456789")
    assert len(code["code"]) == 8 and set(code["code"]) <= alphabet, code

    # 2. Wrong and own codes are refused.
    assert rpc(c, "redeem_pair_code", {"p_code": "ZZZZ-ZZZZ"})[1] == "code_invalid"
    assert rpc(a, "redeem_pair_code", {"p_code": code["code"]})[1] == "own_code"

    # 3. B pairs with A; a second pairing for either is refused.
    pretty = code["code"][:4] + "-" + code["code"][4:].lower()
    assert rpc(b, "redeem_pair_code", {"p_code": pretty})[1] == "ok"
    status, second = rpc(b, "create_pair_code")
    assert status >= 400 and "already_paired" in json.dumps(second), second

    # 4. A writes a row; B (partner) sees it, C (stranger) does not.
    row = tx_row("A")
    status, body = call("POST", "/rest/v1/transactions", a, [row], prefer="return=minimal")
    assert status == 201, body
    assert len(rows(b, f"transactions?user_id=eq.{a_id}")) == 1
    assert rows(c, f"transactions?user_id=eq.{a_id}") == []

    # 5. C cannot write as A; B cannot change A's row.
    forged = dict(tx_row("forged"), user_id=a_id)
    status, body = call("POST", "/rest/v1/transactions", c, [forged], prefer="return=minimal")
    assert status in (401, 403), f"stranger wrote a row as A: {status} {body}"
    hijack = dict(row, merchant="hijacked")
    status, body = call("POST", "/rest/v1/transactions", b, [hijack],
                        prefer="resolution=merge-duplicates,return=minimal")
    assert status in (401, 403, 409), f"partner overwrote A's row via upsert: {status} {body}"
    status, body = call("PATCH", f"/rest/v1/transactions?id=eq.{row['id']}", b,
                        {"merchant": "hijacked"}, prefer="return=representation")
    assert body == [], f"partner updated A's row: {body}"
    assert rows(a, f"transactions?id=eq.{row['id']}")[0]["merchant"] == "test A"

    # 6. Guessing is rate limited.
    results = [rpc(c, "redeem_pair_code", {"p_code": "QQQQQQQQ"})[1] for _ in range(11)]
    assert results[-1] == "too_many_attempts", results

    # 7. The stranger sees no couple, no profiles, no codes.
    call("POST", "/rest/v1/profiles", a, {"user_id": a_id, "display_name": "A"},
         prefer="resolution=merge-duplicates,return=minimal")
    assert rows(c, "couples") == []
    assert rows(c, f"profiles?user_id=eq.{a_id}") == []
    assert len(rows(b, f"profiles?user_id=eq.{a_id}")) == 1
    status, body = call("GET", "/rest/v1/pair_codes", c)
    assert status != 200 or body == [], body

    # 8. Unpairing removes both people's rows and the access.
    assert rpc(a, "unpair")[0] in (200, 204)
    assert rows(b, f"transactions?user_id=eq.{a_id}") == []
    assert rows(a, "transactions") == []
    assert rows(b, "couples") == []

    print("RLS OK")


if __name__ == "__main__":
    main()
