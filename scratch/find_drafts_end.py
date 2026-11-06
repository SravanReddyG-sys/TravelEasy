with open(r'hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java', 'r', encoding='utf-8') as f:
    text = f.read()

idx = text.find('getDraftsByManager')
print(text[idx:idx+1000])
