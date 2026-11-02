"""Make selected ranking caches cold on a local Redis without flushing Redis."""

import argparse
import socket


CACHES = {
    "leaguePlayerRankings",
    "leagueTeamRankings",
    "teamPlayerRankings",
}


def read_reply(stream):
    kind = stream.read(1)
    line = stream.readline().rstrip(b"\r\n")
    if kind == b"-":
        raise RuntimeError(f"Redis command failed: {line.decode(errors='replace')}")
    if kind == b"+":
        return line.decode()
    if kind == b":":
        return int(line)
    if kind == b"$":
        size = int(line)
        if size < 0:
            return None
        value = stream.read(size)
        stream.read(2)
        return value.decode()
    if kind == b"*":
        return [read_reply(stream) for _ in range(int(line))]
    raise RuntimeError("Unexpected Redis response")


def command(stream, connection, *parts):
    payload = f"*{len(parts)}\r\n".encode()
    for part in parts:
        value = str(part).encode()
        payload += f"${len(value)}\r\n".encode() + value + b"\r\n"
    connection.sendall(payload)
    return read_reply(stream)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("caches", nargs="+", choices=sorted(CACHES))
    parser.add_argument("--port", type=int, default=6379)
    parser.add_argument("--clear-baseline", action="store_true",
                        help="Remove only selected cacheName::* entries for the non-locking baseline")
    parser.add_argument("--inspect", action="store_true",
                        help="Count raw and versioned keys without changing Redis")
    args = parser.parse_args()

    with socket.create_connection(("127.0.0.1", args.port), timeout=3) as connection:
        with connection.makefile("rb") as stream:
            if command(stream, connection, "PING") != "PONG":
                raise RuntimeError("Local Redis did not answer PING")
            for cache_name in args.caches:
                if args.clear_baseline or args.inspect:
                    removed = 0
                    raw = 0
                    versioned = 0
                    cursor = "0"
                    while True:
                        cursor, keys = command(stream, connection, "SCAN", cursor,
                                               "MATCH", f"{cache_name}::*", "COUNT", 100)
                        if keys:
                            if args.inspect:
                                for key in keys:
                                    suffix = key.split("::", 1)[1]
                                    if suffix.split(":", 1)[0].isdigit():
                                        versioned += 1
                                    else:
                                        raw += 1
                            else:
                                removed += command(stream, connection, "UNLINK", *keys)
                        if cursor == "0":
                            break
                    if args.inspect:
                        print(f"{cache_name}: raw={raw} versioned={versioned}")
                    else:
                        print(f"{cache_name}: removed={removed}")
                else:
                    epoch = command(stream, connection, "INCR",
                                    f"match-vault:ranking-cache:epoch:{cache_name}")
                    print(f"{cache_name}: epoch={epoch}")


if __name__ == "__main__":
    main()
