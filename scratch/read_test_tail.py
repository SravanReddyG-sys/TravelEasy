with open(r'hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java', 'r', encoding='utf-8') as f:
    text = f.read()

idx = text.find("testImageStorage_Validation")
print(text[idx:idx+1500])
