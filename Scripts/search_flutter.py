import os

root_dir = r"c:\Users\sugee\Documents\GitHub\AmazeContinuityProjects\AmazeCC\lib"
for dirpath, _, filenames in os.walk(root_dir):
    for f in filenames:
        if f.endswith('.dart'):
            with open(os.path.join(dirpath, f), 'r', encoding='utf-8', errors='ignore') as file:
                content = file.read()
                if 'freeclass' in content.lower() or 'free class' in content.lower():
                    print("Found in:", os.path.join(dirpath, f))
