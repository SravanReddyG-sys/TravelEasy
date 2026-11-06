path = r'frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()
target = "import { HotelService } from '../../services/hotel.service';"
replacement = target + "\nimport { Hotel, HotelRoom, HotelImage, HotelStep1Request, HotelStep1Response } from '../../models/hotel.model';"
if target in c and 'HotelImage' not in c[:500]:
    c = c.replace(target, replacement, 1)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)
    print('Successfully added import')
else:
    print('Target not matched or already imported')
