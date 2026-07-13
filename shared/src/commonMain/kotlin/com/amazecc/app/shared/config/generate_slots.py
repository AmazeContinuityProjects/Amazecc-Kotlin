import json
import os

in_path = 'c:/Users/sugee/Documents/GitHub/AmazeContinuityProjects/AmazeCC/config.json'
out_path = 'c:/Users/sugee/Documents/GitHub/AmazeContinuityProjects/Amazecc-Kotlin/shared/src/commonMain/kotlin/com/amazecc/app/shared/config/SlotMap.kt'

os.makedirs(os.path.dirname(out_path), exist_ok=True)

with open(in_path, 'r') as f:
    config = json.load(f)

slotMap = config.get('slotMap', {})
out = []
out.append('package com.amazecc.app.shared.config\n\n')
out.append('object SlotMap {\n')
out.append('    val map = mapOf(\n')
for day, slots in slotMap.items():
    out.append(f'        "{day}" to mapOf(\n')
    for slot, data in slots.items():
        out.append(f'            "{slot}" to "{data["time"]}",\n')
    out.append('        ),\n')
out.append('    )\n')
out.append('}\n')

with open(out_path, 'w') as f:
    f.writelines(out)
print('Generated SlotMap.kt')
