with open(r'hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java', 'r', encoding='utf-8') as f:
    text = f.read()

print("Length of test file:", len(text))
print("Start:")
print(text[:800])
