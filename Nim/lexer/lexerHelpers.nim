import std/[strutils]

# Wrappers around isDigit and isAlphanumeric for
# strings
proc isDigit*(s: string): bool =
  for c in s:
    if not c.isDigit():
      return false
  return true

proc isAlphaNumeric*(s: string): bool =
  for c in s:
    if not c.isAlphaNumeric():
      return false
  return true