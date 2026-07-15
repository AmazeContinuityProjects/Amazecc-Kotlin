import re

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/FreeClassroomsScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the direct decodeFromString with a safe try-catch
old_code = "val schema = remember { json.decodeFromString<CampusSchema>(CampusSchemas.CHENNAI_JSON) }"
new_code = """val schema = remember { 
        try {
            json.decodeFromString<CampusSchema>(CampusSchemas.CHENNAI_JSON) 
        } catch (e: Exception) {
            CampusSchema()
        }
    }"""

content = content.replace(old_code, new_code)

with open('shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/FreeClassroomsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
