service_path = r"hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java"
with open(service_path, "r", encoding="utf-8") as f:
    code = f.read()

if "import java.util.stream.Collectors;" not in code:
    code = code.replace("import java.util.*;", "import java.util.*;\nimport java.util.stream.Collectors;")
    with open(service_path, "w", encoding="utf-8") as f:
        f.write(code)
    print("Added stream.Collectors import successfully")
else:
    print("Already present")
