import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/api/AmazeClient.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_code = """                message = if (hasDuesVal == false) duesResp["message"]?.jsonPrimitive?.content else null"""
new_code = """                message = if (hasDuesVal == false) duesResp?.get("message")?.jsonPrimitive?.content else null"""

content = content.replace(old_code, new_code)

old_code_2 = """                    description = duesResp["message"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "Pending Dues","""
new_code_2 = """                    description = duesResp?.get("message")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "Pending Dues","""

content = content.replace(old_code_2, new_code_2)

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/api/AmazeClient.kt', 'w', encoding='utf-8') as f:
    f.write(content)
