"""Keep CurseForge list markers beside their text without changing release-note content."""
import re

_ITEM = re.compile(r"^([ \t]*)(?:[-+*]|\d+[.)])\s+\S")
_FENCE = re.compile(r"^[ \t]*(`{3,}|~{3,})")


def tight_changelog(notes):
    lines = notes.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    result = []
    fence = None
    for index, line in enumerate(lines):
        match = _FENCE.match(line)
        if match:
            delimiter = match.group(1)
            if fence is None:
                fence = delimiter
            elif delimiter[0] == fence[0] and len(delimiter) >= len(fence):
                fence = None
            result.append(line)
            continue
        if fence is None and not line.strip() and result:
            next_index = index + 1
            while next_index < len(lines) and not lines[next_index].strip():
                next_index += 1
            previous = _ITEM.match(result[-1])
            following = _ITEM.match(lines[next_index]) if next_index < len(lines) else None
            if previous and following and previous.group(1) == following.group(1):
                continue
        result.append(line)
    return "\n".join(result).rstrip() + "\n"
