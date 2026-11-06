with open(r'hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java', 'r', encoding='utf-8') as f:
    text = f.read()

idx = text.find("testClassification_5StarDeluxe")
print(text[idx-100:idx+400])
