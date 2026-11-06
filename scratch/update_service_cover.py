with open('hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_block = [
    '        long coverCount = request.getImages().stream().filter(HotelImageDto::isCover).count();\n',
    '        if (coverCount == 0 && !request.getImages().isEmpty()) {\n',
    '            // Safe fallback: in the 4-tile UI, Tile 1 is the dedicated Cover Image slot\n',
    '            request.getImages().get(0).setCover(true);\n',
    '            request.getImages().get(0).setIsCover(true);\n',
    '            coverCount = 1;\n',
    '        }\n',
    '        if (coverCount != 1) {\n',
    '            throw new DomainValidationException("Exactly one image must be designated as the Cover Image (found: " + coverCount + ")");\n',
    '        }\n'
]
lines[416:420] = new_block

with open('hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java', 'w', encoding='utf-8') as f:
    f.writelines(lines)
print('Updated HotelPropertyService successfully')
