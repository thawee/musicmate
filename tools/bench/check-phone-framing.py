"""Read-only framing checks against the installed phone APK via forwarded port 19000."""
import json
import pathlib
import socket

cases = [
    ("conflicting-lines", "Content-Length: 5\r\nContent-Length: 6", 400),
    ("conflicting-list", "Content-Length: 5,6", 400),
    ("signed-length", "Content-Length: +5", 400),
    ("empty-list-element", "Content-Length: 5,", 400),
    ("overflow", "Content-Length: 9223372036854775808", 400),
    ("whitespace-before-colon", "Content-Length : 5", 400),
    ("folded-field", "Content-Length: 5\r\n ,6", 400),
    ("missing-colon", "Content-Length: 5\r\ninvalid-field", 400),
    ("te-and-cl", "Content-Length: 5\r\nTransfer-Encoding: chunked", 400),
    ("unframed-coding", "Transfer-Encoding: gzip", 400),
    ("wrong-final-coding", "Transfer-Encoding: chunked,gzip", 400),
    ("repeated-te", "Transfer-Encoding: chunked\r\nTransfer-Encoding: chunked", 400),
    ("chunked", "Transfer-Encoding: chunked", 501),
    ("coding-chain", "Transfer-Encoding: gzip, chunked", 501),
    ("oversized", "Content-Length: 2147483647", 413),
]
results = []


def exchange(raw):
    with socket.create_connection(("127.0.0.1", 19000), timeout=5) as client:
        client.sendall(raw.encode("ascii"))
        with client.makefile("rb") as stream:
            first = stream.readline(8192)
            status = int(first.split()[1])
            headers = {}
            for _ in range(128):
                line = stream.readline(8192)
                if line == b"\r\n": break
                if not line: raise AssertionError("incomplete headers")
                name, value = line.decode("ascii").split(":", 1)
                headers[name.lower()] = value.strip()
            else: raise AssertionError("too many headers")
            body = stream.read(int(headers["content-length"]))
            assert len(body) == int(headers["content-length"])
            assert headers["connection"].lower() == "close" and stream.read(1) == b""
            return status, body


for name, fields, expected in cases:
    status, _ = exchange("POST /__parser_probe__ HTTP/1.1\r\nHost: test\r\n" + fields
                         + "\r\n\r\nhelloGET /__parser_probe__ HTTP/1.1\r\nHost: test\r\n\r\n")
    assert status == expected, (name, status, expected)
    results.append({"case": name, "expected": expected, "actual": status, "closed": True})
for name, fields in (("identical-lines", "Content-Length: 5\r\nContent-Length: 005"),
                     ("identical-list", "Content-Length: 5, 005")):
    status, body = exchange("GET /music/2122216336/file HTTP/1.1\r\nHost: test\r\nRange: bytes=0-0\r\n"
                            + "Connection: close\r\n" + fields + "\r\n\r\nhello")
    assert status == 206 and body == b"f", (name, status, body)
    results.append({"case": name, "expected": 206, "actual": status, "closed": True, "exact_byte": True})
pathlib.Path("/private/tmp/musicmate-parser-20261003/framing-phone.json").write_text(json.dumps(results, indent=2) + "\n")
print(f"{len(results)} phone framing cases passed")
