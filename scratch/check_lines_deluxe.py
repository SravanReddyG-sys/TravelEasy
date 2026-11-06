with open(r'hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "testClassification_5StarDeluxe" in line:
        for j in range(max(0, i-4), min(i+15, len(lines))):
            print(f"{j+1}: {repr(lines[j])}")
        break
