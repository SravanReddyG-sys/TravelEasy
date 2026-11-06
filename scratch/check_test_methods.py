with open(r'hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java', 'r', encoding='utf-8') as f:
    text = f.read()

idx = text.find("testPropertySegment_Validation")
print(text[idx-50:idx+1200])
