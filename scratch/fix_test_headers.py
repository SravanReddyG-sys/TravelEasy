test_path = r"hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java"
with open(test_path, "r", encoding="utf-8") as f:
    code = f.read()

target = """        // Manager 1 successfully deletes own image -> 200 OK
        ResponseEntity<Map> ownerDeleteRes = restTemplate.exchange(getBaseUrl() + "/images/" + filename,
                HttpMethod.DELETE, new HttpEntity<>(headers1), Map.class);"""

replacement = """        // Manager 1 successfully deletes own image -> 200 OK
        ResponseEntity<Map> ownerDeleteRes = restTemplate.exchange(getBaseUrl() + "/images/" + filename,
                HttpMethod.DELETE, new HttpEntity<>(createAuthHeaders(managerToken)), Map.class);"""

assert target in code, "target not found"
code = code.replace(target, replacement, 1)

with open(test_path, "w", encoding="utf-8") as f:
    f.write(code)

print("Fixed testImageSecurity_ManagerIsolation successfully!")
