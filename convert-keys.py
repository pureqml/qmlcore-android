#!/usr/bin/env python3
import re

key_re = re.compile('(KEYCODE_([a-zA-Z0-9_]+))+\s*=\s*(\d+)')

with open('KeyEvent.java', 'rt') as f:
	data = f.read()

for m in key_re.findall(data):
	macro, name, code = m
	print("case KeyEvent.%s: return \"%s\";" %(macro, "".join(map(str.capitalize, name.split("_")))))
