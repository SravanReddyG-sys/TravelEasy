with open(r'hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java', 'r', encoding='utf-8') as f:
    text = f.read()

idx = text.find("testImageStorage_Validation")
print(text[idx-50:idx+1500])
