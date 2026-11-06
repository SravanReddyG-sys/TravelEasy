with open(r'hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java', 'r', encoding='utf-8') as f:
    text = f.read()

idx = text.find('getMyDrafts')
print(text[idx-50:idx+400])
