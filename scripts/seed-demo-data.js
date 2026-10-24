/**
 * Travel Easy - Automated Demo Data Seeder
 * ========================================
 * Populates realistic, interconnected demonstration data for bus travel,
 * hotel booking, customer reservations, payments, reviews, and notifications.
 *
 * Designed for microservices architecture with database-per-service pattern:
 * Interacts exclusively through supported service REST APIs.
 * Fully idempotent: Safe to execute repeatedly without duplicating data.
 */

const BASE_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080/api';

async function request(endpoint, options = {}) {
  const url = `${BASE_URL}${endpoint}`;
  try {
    const res = await fetch(url, {
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...(options.headers || {})
      },
      ...options
    });
    const text = await res.text();
    let data;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = text;
    }
    return { ok: res.ok, status: res.status, data };
  } catch (err) {
    return { ok: false, status: 0, error: err.message };
  }
}

// Stats tracking
const stats = {
  busesCreated: 0,
  busesExisting: 0,
  schedulesCreated: 0,
  schedulesExisting: 0,
  hotelsCreated: 0,
  hotelsExisting: 0,
  roomsCreated: 0,
  roomsExisting: 0,
  bookingsCreated: 0,
  bookingsExisting: 0,
  paymentsCreated: 0,
  paymentsExisting: 0,
  reviewsCreated: 0,
  reviewsExisting: 0,
  notificationsCreated: 0,
  notificationsExisting: 0
};

async function seedBuses() {
  console.log('\n🚌 --- [1/4] Seeding Bus Fleet & Schedules ---');

  // 1. Fetch existing buses
  const busListRes = await request('/buses');
  const existingBuses = busListRes.ok && Array.isArray(busListRes.data) ? busListRes.data : [];

  const demoBuses = [
    {
      operatorId: 10,
      operatorName: 'GSR Travels',
      busNumber: 'KA-01-AK-7788',
      busType: 'BharatBenz AC Sleeper (2+1)',
      totalSeats: 36,
      amenities: 'Wi-Fi, Personal Charging Points, Live Tracking, Water Bottle, Blanket'
    },
    {
      operatorId: 10,
      operatorName: 'GSR Travels',
      busNumber: 'AP-39-TG-9900',
      busType: 'Volvo 9600 Multi-Axle AC Sleeper',
      totalSeats: 40,
      amenities: 'Wi-Fi, Individual TV Screens, Pillow & Blanket, Snacks, GPS'
    },
    {
      operatorId: 10,
      operatorName: 'GSR Travels',
      busNumber: 'KA-51-EX-1212',
      busType: 'Scania Multi-Axle Semi-Sleeper AC',
      totalSeats: 44,
      amenities: 'Wi-Fi, USB Chargers, Emergency Exit, Reading Lights'
    },
    {
      operatorId: 2,
      operatorName: 'Express Bus Travels',
      busNumber: 'KA-04-BT-5544',
      busType: 'Volvo B11R Multi-Axle AC Sleeper',
      totalSeats: 36,
      amenities: 'Wi-Fi, Water Bottle, Charging Point, Reading Lamp, GPS'
    },
    {
      operatorId: 2,
      operatorName: 'Express Bus Travels',
      busNumber: 'TS-09-UB-8822',
      busType: 'Mercedes-Benz Super Luxury Semi-Sleeper',
      totalSeats: 40,
      amenities: 'Air Suspension, Reclining Seats, Mineral Water, CCTV'
    }
  ];

  const busMap = new Map();
  // Map existing buses first
  for (const b of existingBuses) {
    busMap.set(b.busNumber, b);
  }

  // Create missing buses
  for (const bDef of demoBuses) {
    if (busMap.has(bDef.busNumber)) {
      stats.busesExisting++;
    } else {
      const res = await request('/buses', {
        method: 'POST',
        body: JSON.stringify(bDef)
      });
      if (res.ok) {
        busMap.set(bDef.busNumber, res.data);
        stats.busesCreated++;
        console.log(`  ✓ Created Bus: ${bDef.busNumber} (${bDef.busType}) for ${bDef.operatorName}`);
      } else {
        console.error(`  ✗ Failed to create bus ${bDef.busNumber}:`, res.data);
      }
    }
  }

  console.log(`  Bus fleet ready. (Created: ${stats.busesCreated}, Existing: ${stats.busesExisting})`);

  // 2. Define multi-day schedules (Today: 2026-10-16, Tomorrow: 2026-10-17, Weekend: 2026-10-18, 2026-10-19)
  const busA = busMap.get('KA-01-AK-7788') || existingBuses[0];
  const busB = busMap.get('AP-39-TG-9900') || existingBuses[1];
  const busC = busMap.get('KA-51-EX-1212') || existingBuses[2];
  const busD = busMap.get('KA-04-BT-5544') || existingBuses[3];
  const busE = busMap.get('TS-09-UB-8822') || existingBuses[4];

  const demoSchedules = [
    // --- 2026-10-16 (Today) ---
    {
      busId: busA.id,
      origin: 'Bengaluru',
      destination: 'Hyderabad',
      travelDate: '2026-10-16',
      departureTime: '21:00',
      arrivalTime: '06:30',
      fare: 1150.0,
      boardingPoints: 'Majestic (21:00), Hebbal (21:40), Kempegowda Airport Toll (22:15)',
      droppingPoints: 'Shamshabad (05:45), Gachibowli (06:15), Ameerpet (06:30)'
    },
    {
      busId: busB.id,
      origin: 'Hyderabad',
      destination: 'Bengaluru',
      travelDate: '2026-10-16',
      departureTime: '21:30',
      arrivalTime: '07:00',
      fare: 1200.0,
      boardingPoints: 'Ameerpet (21:30), Gachibowli (22:00), Shamshabad (22:30)',
      droppingPoints: 'Hebbal (06:15), Majestic (06:45), Electronic City (07:15)'
    },
    {
      busId: busD.id,
      origin: 'Bengaluru',
      destination: 'Hyderabad',
      travelDate: '2026-10-16',
      departureTime: '22:15',
      arrivalTime: '07:45',
      fare: 1050.0,
      boardingPoints: 'Kalaisipalyam (22:15), Tin Factory (22:45), Yelahanka (23:15)',
      droppingPoints: 'Kurnool Bypass (04:15), Shamshabad (07:00), MGBS (07:45)'
    },
    {
      busId: busC.id,
      origin: 'Hyderabad',
      destination: 'Vijayawada',
      travelDate: '2026-10-16',
      departureTime: '23:00',
      arrivalTime: '04:45',
      fare: 850.0,
      boardingPoints: 'MGBS (23:00), LB Nagar (23:30), Vanasthalipuram (23:45)',
      droppingPoints: 'Gollapudi (04:15), Benz Circle (04:35), PNBS (04:45)'
    },
    {
      busId: busE.id,
      origin: 'Vijayawada',
      destination: 'Hyderabad',
      travelDate: '2026-10-16',
      departureTime: '14:30',
      arrivalTime: '20:00',
      fare: 750.0,
      boardingPoints: 'Pandit Nehru Bus Station (14:30), Ramavarappadu (14:50)',
      droppingPoints: 'LB Nagar (19:30), MGBS (20:00)'
    },
    {
      busId: busC.id,
      origin: 'Bengaluru',
      destination: 'Chennai',
      travelDate: '2026-10-16',
      departureTime: '23:15',
      arrivalTime: '05:45',
      fare: 750.0,
      boardingPoints: 'Shanthinagar (23:15), Indiranagar (23:40), Hosur (00:30)',
      droppingPoints: 'Poonamallee (04:45), Koyambedu CMBT (05:15), Guindy (05:45)'
    },
    {
      busId: busE.id,
      origin: 'Chennai',
      destination: 'Bengaluru',
      travelDate: '2026-10-16',
      departureTime: '22:30',
      arrivalTime: '05:15',
      fare: 800.0,
      boardingPoints: 'Koyambedu CMBT (22:30), Guindy (23:00), Sriperumbudur (23:45)',
      droppingPoints: 'Electronic City (04:30), Silk Board (04:50), Shanthinagar (05:15)'
    },
    {
      busId: busA.id,
      origin: 'Bengaluru',
      destination: 'Goa',
      travelDate: '2026-10-16',
      departureTime: '19:30',
      arrivalTime: '07:30',
      fare: 1450.0,
      boardingPoints: 'Majestic (19:30), Yeshwantpur (20:00), Goraguntepalya (20:15)',
      droppingPoints: 'Madgaon Railway Station (06:30), Panaji KTC (07:15), Mapusa (07:30)'
    },
    {
      busId: busB.id,
      origin: 'Goa',
      destination: 'Bengaluru',
      travelDate: '2026-10-16',
      departureTime: '18:30',
      arrivalTime: '06:30',
      fare: 1500.0,
      boardingPoints: 'Mapusa (18:30), Panaji (19:00), Madgaon (19:45)',
      droppingPoints: 'Yeshwantpur (05:45), Majestic (06:15), Shanthinagar (06:30)'
    },

    // --- 2026-10-17 (Tomorrow) ---
    {
      busId: busA.id,
      origin: 'Hyderabad',
      destination: 'Bengaluru',
      travelDate: '2026-10-17',
      departureTime: '21:30',
      arrivalTime: '07:00',
      fare: 1150.0,
      boardingPoints: 'Ameerpet (21:30), Gachibowli (22:00), Shamshabad (22:30)',
      droppingPoints: 'Hebbal (06:15), Majestic (06:45), Electronic City (07:15)'
    },
    {
      busId: busB.id,
      origin: 'Bengaluru',
      destination: 'Hyderabad',
      travelDate: '2026-10-17',
      departureTime: '21:00',
      arrivalTime: '06:30',
      fare: 1200.0,
      boardingPoints: 'Majestic (21:00), Hebbal (21:40), Kempegowda Airport Toll (22:15)',
      droppingPoints: 'Shamshabad (05:45), Gachibowli (06:15), Ameerpet (06:30)'
    },
    {
      busId: busC.id,
      origin: 'Vijayawada',
      destination: 'Hyderabad',
      travelDate: '2026-10-17',
      departureTime: '22:30',
      arrivalTime: '05:00',
      fare: 850.0,
      boardingPoints: 'Pandit Nehru Bus Station (22:30), Benz Circle (22:45)',
      droppingPoints: 'LB Nagar (04:15), MGBS (05:00)'
    },
    {
      busId: busE.id,
      origin: 'Hyderabad',
      destination: 'Vijayawada',
      travelDate: '2026-10-17',
      departureTime: '06:30',
      arrivalTime: '12:00',
      fare: 750.0,
      boardingPoints: 'MGBS (06:30), LB Nagar (07:00)',
      droppingPoints: 'Gollapudi (11:30), Benz Circle (11:50), PNBS (12:00)'
    },
    {
      busId: busC.id,
      origin: 'Chennai',
      destination: 'Bengaluru',
      travelDate: '2026-10-17',
      departureTime: '22:30',
      arrivalTime: '05:15',
      fare: 800.0,
      boardingPoints: 'Koyambedu CMBT (22:30), Guindy (23:00)',
      droppingPoints: 'Electronic City (04:30), Shanthinagar (05:15)'
    },
    {
      busId: busE.id,
      origin: 'Bengaluru',
      destination: 'Chennai',
      travelDate: '2026-10-17',
      departureTime: '23:15',
      arrivalTime: '05:45',
      fare: 750.0,
      boardingPoints: 'Shanthinagar (23:15), Indiranagar (23:40)',
      droppingPoints: 'Koyambedu CMBT (05:15), Guindy (05:45)'
    },
    {
      busId: busA.id,
      origin: 'Goa',
      destination: 'Bengaluru',
      travelDate: '2026-10-17',
      departureTime: '18:30',
      arrivalTime: '06:30',
      fare: 1500.0,
      boardingPoints: 'Mapusa (18:30), Panaji (19:00), Madgaon (19:45)',
      droppingPoints: 'Majestic (06:15), Shanthinagar (06:30)'
    },
    {
      busId: busB.id,
      origin: 'Bengaluru',
      destination: 'Goa',
      travelDate: '2026-10-17',
      departureTime: '19:30',
      arrivalTime: '07:30',
      fare: 1450.0,
      boardingPoints: 'Majestic (19:30), Yeshwantpur (20:00)',
      droppingPoints: 'Panaji KTC (07:15), Mapusa (07:30)'
    },

    // --- 2026-10-18 (Weekend Saturday) ---
    {
      busId: busA.id,
      origin: 'Bengaluru',
      destination: 'Hyderabad',
      travelDate: '2026-10-18',
      departureTime: '21:00',
      arrivalTime: '06:30',
      fare: 1250.0,
      boardingPoints: 'Majestic (21:00), Hebbal (21:40)',
      droppingPoints: 'Shamshabad (05:45), Ameerpet (06:30)'
    },
    {
      busId: busB.id,
      origin: 'Hyderabad',
      destination: 'Bengaluru',
      travelDate: '2026-10-18',
      departureTime: '21:30',
      arrivalTime: '07:00',
      fare: 1250.0,
      boardingPoints: 'Ameerpet (21:30), Gachibowli (22:00)',
      droppingPoints: 'Hebbal (06:15), Majestic (06:45)'
    },
    {
      busId: busA.id,
      origin: 'Bengaluru',
      destination: 'Goa',
      travelDate: '2026-10-18',
      departureTime: '19:30',
      arrivalTime: '07:30',
      fare: 1550.0,
      boardingPoints: 'Majestic (19:30), Yeshwantpur (20:00)',
      droppingPoints: 'Madgaon (06:30), Panaji (07:15)'
    }
  ];

  const seededSchedules = [];

  for (const sDef of demoSchedules) {
    // Check if matching schedule already exists
    const searchRes = await request(
      `/buses/search?origin=${encodeURIComponent(sDef.origin)}&destination=${encodeURIComponent(sDef.destination)}&date=${sDef.travelDate}`
    );
    const existing = searchRes.ok && Array.isArray(searchRes.data)
      ? searchRes.data.find(s => s.busId === sDef.busId && s.departureTime === sDef.departureTime)
      : null;

    if (existing) {
      stats.schedulesExisting++;
      seededSchedules.push(existing);
    } else {
      const createRes = await request('/buses/schedules', {
        method: 'POST',
        body: JSON.stringify(sDef)
      });
      if (createRes.ok) {
        stats.schedulesCreated++;
        seededSchedules.push(createRes.data);
        console.log(`  ✓ Created Schedule: ${sDef.origin} ➔ ${sDef.destination} on ${sDef.travelDate} at ${sDef.departureTime} (Fare: ₹${sDef.fare})`);
      } else {
        console.error(`  ✗ Failed to create schedule ${sDef.origin}->${sDef.destination}:`, createRes.data);
      }
    }
  }

  console.log(`  Bus schedules ready. (Created: ${stats.schedulesCreated}, Existing: ${stats.schedulesExisting})`);
  return { buses: busMap, schedules: seededSchedules };
}

async function seedHotels() {
  console.log('\n🏨 --- [2/4] Seeding Hotel Properties & Room Types ---');

  // Fetch existing hotels
  const hotelListRes = await request('/hotels');
  const existingHotels = hotelListRes.ok && Array.isArray(hotelListRes.data) ? hotelListRes.data : [];

  const demoHotels = [
    {
      managerId: 11,
      managerName: 'Sudheer Bandi',
      name: 'The Oberoi Bengaluru',
      city: 'Bengaluru',
      address: '37-39, Mahatma Gandhi Road, Yellappa Garden, Bengaluru, Karnataka 560001',
      starRating: 5,
      description: "Award-winning luxury hotel nestled in lush tropical gardens in the heart of Bengaluru's central business district, featuring tranquil pools and signature fine dining.",
      amenities: 'Swimming Pool, Spa & Wellness Centre, High-speed Wi-Fi, 24-hr Room Service, Fitness Centre, Award-winning Dining',
      imageUrl: 'assets/images/bengaluru.png',
      rooms: [
        {
          roomTypeName: 'Deluxe Garden View Room',
          maxOccupancy: 2,
          pricePerNight: 6800.0,
          totalRooms: 15,
          availableRooms: 15,
          amenities: 'King Bed, Garden Balcony, Marble Bathroom, Free High-Speed Wi-Fi, Complimentary Breakfast'
        },
        {
          roomTypeName: 'Premier Executive Suite',
          maxOccupancy: 3,
          pricePerNight: 11500.0,
          totalRooms: 8,
          availableRooms: 8,
          amenities: 'Separate Living Room, King Bed, Butler Service, Airport Pickup, Complimentary Cocktails'
        }
      ]
    },
    {
      managerId: 19,
      managerName: 'Sravan Residence',
      name: 'Taj Exotica Resort & Spa Goa',
      city: 'Goa',
      address: 'Calwaddo, Benaulim, Salcete, Goa 403716',
      starRating: 5,
      description: 'Mediterranean-inspired 5-star beachfront resort overlooking the Arabian Sea, featuring 56 acres of lush gardens, private beach access, world-class golf, and gourmet seafood dining.',
      amenities: 'Private Beach Access, Infinity Pool, Jiva Ayurvedic Spa, Tennis Courts, Beachfront Dining, Water Sports',
      imageUrl: 'assets/images/goa.png',
      rooms: [
        {
          roomTypeName: 'Luxury Garden Villa',
          maxOccupancy: 2,
          pricePerNight: 9200.0,
          totalRooms: 12,
          availableRooms: 12,
          amenities: 'King Bed, Private Verandah, Plunge Pool Access, Complimentary Breakfast, Lush Garden Views'
        },
        {
          roomTypeName: 'Sunset Sea-Facing Villa',
          maxOccupancy: 3,
          pricePerNight: 14500.0,
          totalRooms: 8,
          availableRooms: 8,
          amenities: 'Panoramic Ocean View, King Bed, Jacuzzi, Sunset Cocktail Hour, Private Beach Sunbed'
        },
        {
          roomTypeName: 'Presidential Sea Suite',
          maxOccupancy: 4,
          pricePerNight: 22000.0,
          totalRooms: 4,
          availableRooms: 4,
          amenities: '2 Master Bedrooms, Private Lawn, Sea View Deck, Dedicated Butler Service, In-villa Dining'
        }
      ]
    },
    {
      managerId: 3,
      managerName: 'Grand Residency Hotel',
      name: 'ITC Grand Chola Chennai',
      city: 'Chennai',
      address: '63, Anna Salai, Guindy, Chennai, Tamil Nadu 600032',
      starRating: 5,
      description: 'Magnificent luxury hotel inspired by the glorious Chola dynasty architecture, featuring 10 distinctive dining destinations, 3 swimming pools, and the signature Kaya Kalp Royal Spa.',
      amenities: '3 Swimming Pools, Kaya Kalp Spa, 10 Restaurants, Helipad, Luxury Shopping Arcade, Fitness Centre',
      imageUrl: 'assets/images/chennai.png',
      rooms: [
        {
          roomTypeName: 'Executive Club Room',
          maxOccupancy: 2,
          pricePerNight: 5900.0,
          totalRooms: 20,
          availableRooms: 20,
          amenities: 'City Skyline View, King Bed, Ergonomic Workstation, High-speed Wi-Fi, Breakfast'
        },
        {
          roomTypeName: 'ITC One Luxury Suite',
          maxOccupancy: 3,
          pricePerNight: 10200.0,
          totalRooms: 10,
          availableRooms: 10,
          amenities: 'Executive Lounge Access, Four-Fixture Marble Bathroom, Complimentary Cocktail Hours'
        }
      ]
    },
    {
      managerId: 19,
      managerName: 'Sravan Residence',
      name: 'Savoy - IHCL SeleQtions Ooty',
      city: 'Ooty',
      address: '77, Sylks Road, Ooty, Tamil Nadu 643001',
      starRating: 5,
      description: 'Historic colonial-era retreat established in 1841 amidst misty Nilgiri hills, featuring wood-burning fireplaces, manicured English gardens, and quintessential British afternoon tea.',
      amenities: 'Fireplace in Every Room, English Garden Lawn, Tea Lounge, Badminton Court, Nature Walk, Mountain View',
      imageUrl: 'assets/images/ooty.png',
      rooms: [
        {
          roomTypeName: 'Heritage Superior Room',
          maxOccupancy: 2,
          pricePerNight: 7500.0,
          totalRooms: 10,
          availableRooms: 10,
          amenities: 'Working Fireplace, Queen Bed, Mountain View, Victorian Decor, Full English Breakfast'
        },
        {
          roomTypeName: 'Garden View Suite',
          maxOccupancy: 3,
          pricePerNight: 11800.0,
          totalRooms: 6,
          availableRooms: 6,
          amenities: 'Private Garden Porch, Fireplace, Separate Living Room, Evening High Tea Experience'
        }
      ]
    },
    {
      managerId: 11,
      managerName: 'Sudheer Bandi',
      name: 'Fragrant Nature Munnar Resort',
      city: 'Munnar',
      address: 'Pothamedu, Munnar, Idukki District, Kerala 685612',
      starRating: 5,
      description: 'Spectacular boutique resort perched high among emerald tea plantations and misty mountains, featuring cozy fireplaces in every room and panoramic valley vistas.',
      amenities: 'Tea Estate Treks, Prana Ayurvedic Spa, Amphitheatre, Valley View Terrace, Multi-Cuisine Dining',
      imageUrl: 'assets/images/munnar.png',
      rooms: [
        {
          roomTypeName: 'Tropic Green Valley Room',
          maxOccupancy: 2,
          pricePerNight: 6400.0,
          totalRooms: 12,
          availableRooms: 12,
          amenities: 'Sweeping Tea Valley View, King Bed, Private Balcony, Complimentary Plantation Breakfast'
        },
        {
          roomTypeName: 'Moonlight Premium Suite',
          maxOccupancy: 3,
          pricePerNight: 9800.0,
          totalRooms: 6,
          availableRooms: 6,
          amenities: 'Panoramic Mist Views, Jacuzzi, Wood Fireplace, Complimentary Guided Tea Tour'
        }
      ]
    },
    {
      managerId: 3,
      managerName: 'Grand Residency Hotel',
      name: 'ITC Kohenur Hyderabad',
      city: 'Hyderabad',
      address: 'Plot No. 5, Survey No. 83/1, HITEC City, Hyderabad, Telangana 500081',
      starRating: 5,
      description: 'Iconic modern luxury hotel overlooking Durgam Cheruvu Lake in HITEC City, featuring bespoke dining by celebrated masterchefs and high-tech guest rooms.',
      amenities: 'Lake View, Rooftop Pool, Luxury Spa, 5 Specialty Restaurants, Executive Lounge, 24/7 Butler Service',
      imageUrl: 'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb',
      rooms: [
        {
          roomTypeName: 'Executive Club Lake View',
          maxOccupancy: 2,
          pricePerNight: 7200.0,
          totalRooms: 15,
          availableRooms: 15,
          amenities: 'Lake View, King Bed, High-speed Wi-Fi, Executive Lounge Access, Breakfast Buffet'
        },
        {
          roomTypeName: 'ITC One Luxury Suite',
          maxOccupancy: 3,
          pricePerNight: 12800.0,
          totalRooms: 8,
          availableRooms: 8,
          amenities: 'Panoramic Glass Facade, Walk-in Wardrobe, Deep Soak Bathtub, Butler Service'
        }
      ]
    }
  ];

  const hotelMap = new Map();
  // Map existing
  for (const h of existingHotels) {
    hotelMap.set(h.name.toLowerCase(), h);
  }

  for (const hDef of demoHotels) {
    let hotel = hotelMap.get(hDef.name.toLowerCase());
    if (hotel) {
      stats.hotelsExisting++;
    } else {
      const payload = {
        managerId: hDef.managerId,
        managerName: hDef.managerName,
        name: hDef.name,
        city: hDef.city,
        address: hDef.address,
        starRating: hDef.starRating,
        description: hDef.description,
        amenities: hDef.amenities,
        imageUrl: hDef.imageUrl
      };
      const createRes = await request('/hotels', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      if (createRes.ok) {
        hotel = createRes.data;
        hotelMap.set(hDef.name.toLowerCase(), hotel);
        stats.hotelsCreated++;
        console.log(`  ✓ Created Hotel: ${hotel.name} in ${hotel.city} (${hotel.starRating}★)`);
      } else {
        console.error(`  ✗ Failed to create hotel ${hDef.name}:`, createRes.data);
        continue;
      }
    }

    // Now seed room types for this hotel
    const detailRes = await request(`/hotels/${hotel.id}`);
    const existingRooms = detailRes.ok && detailRes.data && detailRes.data.rooms ? detailRes.data.rooms : [];
    const existingRoomNames = new Set(existingRooms.map(r => r.roomTypeName.toLowerCase()));

    for (const rDef of hDef.rooms) {
      if (existingRoomNames.has(rDef.roomTypeName.toLowerCase())) {
        stats.roomsExisting++;
      } else {
        const roomRes = await request(`/hotels/${hotel.id}/rooms`, {
          method: 'POST',
          body: JSON.stringify(rDef)
        });
        if (roomRes.ok) {
          stats.roomsCreated++;
          console.log(`    ✓ Added Room: ${rDef.roomTypeName} (₹${rDef.pricePerNight}/night) to ${hotel.name}`);
        } else {
          console.error(`    ✗ Failed to add room ${rDef.roomTypeName}:`, roomRes.data);
        }
      }
    }
  }

  console.log(`  Hotels ready. (Hotels: Created ${stats.hotelsCreated}, Existing ${stats.hotelsExisting} | Rooms: Created ${stats.roomsCreated}, Existing ${stats.roomsExisting})`);
  return hotelMap;
}

async function seedBookingsAndTransactions(busData, hotelMap) {
  console.log('\n💳 --- [3/4] Seeding Sample Bookings, Payments, Reviews & Notifications ---');

  // Find demo schedules to book
  // 1. Bengaluru -> Hyderabad on 2026-10-16
  const blrHydSchedule = busData.schedules.find(
    s => s.origin === 'Bengaluru' && s.destination === 'Hyderabad' && s.travelDate === '2026-10-16'
  );
  // 2. Hyderabad -> Vijayawada on 2026-10-16
  const hydVijSchedule = busData.schedules.find(
    s => s.origin === 'Hyderabad' && s.destination === 'Vijayawada' && s.travelDate === '2026-10-16'
  );
  // 3. Hotel Goa: Taj Exotica
  const goaHotel = hotelMap.get('taj exotica resort & spa goa');
  // 4. Hotel Blr: The Oberoi
  const blrHotel = hotelMap.get('the oberoi bengaluru');

  const demoBookings = [
    {
      bookingRef: 'BKG-DEMO-BUS-01',
      customerId: 4,
      customerName: 'John Doe',
      customerEmail: 'customer@traveleasy.com',
      serviceType: 'BUS',
      serviceId: blrHydSchedule ? blrHydSchedule.id : 1,
      providerId: blrHydSchedule ? blrHydSchedule.busId : 1,
      serviceTitle: blrHydSchedule ? `${blrHydSchedule.operatorName} (${blrHydSchedule.origin} to ${blrHydSchedule.destination})` : 'GSR Travels (Bengaluru to Hyderabad)',
      totalAmount: blrHydSchedule ? blrHydSchedule.fare * 2 : 2300.0,
      detailsJson: `Seats: L1, L2 | Journey Date: ${blrHydSchedule ? blrHydSchedule.travelDate : '2026-10-16'}`,
      status: 'CONFIRMED',
      seatsToBook: ['L1', 'L2'],
      paymentMethod: 'UPI',
      review: {
        rating: 5,
        comment: 'Smooth and pleasant journey with GSR Travels! On-time departure from Majestic, comfortable sleeper berths, and clean blankets.'
      },
      notification: {
        recipientEmail: 'customer@traveleasy.com',
        subject: 'Booking Confirmed: Bengaluru to Hyderabad [BKG-DEMO-BUS-01]',
        message: 'Dear John Doe, your journey from Bengaluru to Hyderabad on 2026-10-16 is confirmed. Seats: L1, L2. Amount paid: ₹2300 via UPI.',
        channel: 'EMAIL'
      }
    },
    {
      bookingRef: 'BKG-DEMO-HTL-01',
      customerId: 4,
      customerName: 'John Doe',
      customerEmail: 'customer@traveleasy.com',
      serviceType: 'HOTEL',
      serviceId: goaHotel ? goaHotel.id : 1,
      providerId: goaHotel ? goaHotel.managerId : 3,
      serviceTitle: `${goaHotel ? goaHotel.name : 'Taj Exotica Resort & Spa Goa'} (Luxury Garden Villa)`,
      totalAmount: 9200.0,
      detailsJson: 'Check-in: 2026-10-16 | Check-out: 2026-10-17 | Guests: 2',
      status: 'CONFIRMED',
      hotelRoomReserve: { hotelName: 'taj exotica resort & spa goa', roomTypeName: 'Luxury Garden Villa', count: 1 },
      paymentMethod: 'CARD',
      review: {
        rating: 5,
        comment: 'Exceptional beachfront stay! The private beach and infinity pool are world-class. Outstanding hospitality from the resort team.'
      },
      notification: {
        recipientEmail: 'customer@traveleasy.com',
        subject: 'Hotel Reservation Confirmed: Taj Exotica Resort & Spa Goa [BKG-DEMO-HTL-01]',
        message: 'Dear John Doe, your reservation at Taj Exotica Resort & Spa Goa is confirmed for 2026-10-16. Total amount: ₹9200 paid via Card.',
        channel: 'EMAIL'
      }
    },
    {
      bookingRef: 'BKG-DEMO-BUS-02',
      customerId: 8,
      customerName: 'Sravan Reddy G',
      customerEmail: 'reddygsravan@gmail.com',
      serviceType: 'BUS',
      serviceId: hydVijSchedule ? hydVijSchedule.id : 2,
      providerId: hydVijSchedule ? hydVijSchedule.busId : 2,
      serviceTitle: hydVijSchedule ? `${hydVijSchedule.operatorName} (${hydVijSchedule.origin} to ${hydVijSchedule.destination})` : 'GSR Travels (Hyderabad to Vijayawada)',
      totalAmount: hydVijSchedule ? hydVijSchedule.fare : 850.0,
      detailsJson: `Seats: L5 | Journey Date: ${hydVijSchedule ? hydVijSchedule.travelDate : '2026-10-16'}`,
      status: 'CONFIRMED',
      seatsToBook: ['L5'],
      paymentMethod: 'NET_BANKING',
      review: {
        rating: 5,
        comment: 'Very punctual and courteous driver. Reached Vijayawada well ahead of time. Highly recommended.'
      },
      notification: {
        recipientEmail: 'reddygsravan@gmail.com',
        subject: 'Bus Booking Confirmed: Hyderabad to Vijayawada [BKG-DEMO-BUS-02]',
        message: 'Dear Sravan, your bus ticket from Hyderabad to Vijayawada on 2026-10-16 is confirmed. Seat: L5. Fare: ₹850 paid via Net Banking.',
        channel: 'EMAIL'
      }
    },
    {
      bookingRef: 'BKG-DEMO-HTL-02',
      customerId: 8,
      customerName: 'Sravan Reddy G',
      customerEmail: 'reddygsravan@gmail.com',
      serviceType: 'HOTEL',
      serviceId: blrHotel ? blrHotel.id : 1,
      providerId: blrHotel ? blrHotel.managerId : 3,
      serviceTitle: `${blrHotel ? blrHotel.name : 'The Oberoi Bengaluru'} (Deluxe Garden View Room)`,
      totalAmount: 6800.0,
      detailsJson: 'Check-in: 2026-10-17 | Check-out: 2026-10-18 | Guests: 2',
      status: 'CONFIRMED',
      hotelRoomReserve: { hotelName: 'the oberoi bengaluru', roomTypeName: 'Deluxe Garden View Room', count: 1 },
      paymentMethod: 'CARD',
      review: {
        rating: 5,
        comment: 'A serene oasis right in the center of MG Road. Impeccable tropical gardens and luxurious rooms.'
      },
      notification: {
        recipientEmail: 'reddygsravan@gmail.com',
        subject: 'Hotel Booking Confirmed: The Oberoi Bengaluru [BKG-DEMO-HTL-02]',
        message: 'Dear Sravan, your reservation at The Oberoi Bengaluru is confirmed for 2026-10-17 to 2026-10-18. Amount: ₹6800 paid via Card.',
        channel: 'EMAIL'
      }
    },
    {
      bookingRef: 'BKG-DEMO-BUS-03-CANC',
      customerId: 4,
      customerName: 'John Doe',
      customerEmail: 'customer@traveleasy.com',
      serviceType: 'BUS',
      serviceId: blrHydSchedule ? blrHydSchedule.id : 1,
      providerId: blrHydSchedule ? blrHydSchedule.busId : 1,
      serviceTitle: 'Express Bus Travels (Bengaluru to Goa)',
      totalAmount: 1450.0,
      detailsJson: 'Seats: L10 | Journey Date: 2026-10-18',
      status: 'CANCELLED',
      paymentMethod: 'CARD',
      notification: {
        recipientEmail: 'customer@traveleasy.com',
        subject: 'Booking Cancellation & Refund Processed [BKG-DEMO-BUS-03-CANC]',
        message: 'Dear John Doe, your booking BKG-DEMO-BUS-03-CANC has been cancelled and full refund of ₹1450 has been processed to your Card.',
        channel: 'EMAIL'
      }
    }
  ];

  for (const bDef of demoBookings) {
    // 1. Check existing booking by bookingRef
    const checkBkgRes = await request(`/bookings/ref/${bDef.bookingRef}`);
    let booking = checkBkgRes.ok && checkBkgRes.data ? checkBkgRes.data : null;

    if (booking) {
      stats.bookingsExisting++;
    } else {
      // Create booking
      const createBkgRes = await request('/bookings', {
        method: 'POST',
        body: JSON.stringify({
          bookingRef: bDef.bookingRef,
          customerId: bDef.customerId,
          customerName: bDef.customerName,
          customerEmail: bDef.customerEmail,
          serviceType: bDef.serviceType,
          serviceId: bDef.serviceId,
          providerId: bDef.providerId,
          serviceTitle: bDef.serviceTitle,
          totalAmount: bDef.totalAmount,
          detailsJson: bDef.detailsJson,
          status: bDef.status
        })
      });

      if (createBkgRes.ok) {
        booking = createBkgRes.data;
        stats.bookingsCreated++;
        console.log(`  ✓ Created Demo Booking: ${booking.bookingRef} (${booking.serviceTitle}) for ${booking.customerName}`);

        // Reserve seats in bus service if applicable
        if (bDef.seatsToBook && bDef.seatsToBook.length > 0 && bDef.serviceId) {
          const seatRes = await request(`/buses/schedules/${bDef.serviceId}/book-seats`, {
            method: 'PUT',
            body: JSON.stringify(bDef.seatsToBook)
          });
          if (seatRes.ok) {
            console.log(`    ✓ Booked Seats ${bDef.seatsToBook.join(', ')} on Schedule #${bDef.serviceId}`);
          }
        }

        // Reserve room in hotel service if applicable
        if (bDef.hotelRoomReserve) {
          const h = hotelMap.get(bDef.hotelRoomReserve.hotelName.toLowerCase());
          if (h) {
            const hDetail = await request(`/hotels/${h.id}`);
            const rm = hDetail.ok && hDetail.data?.rooms?.find(
              r => r.roomTypeName.toLowerCase() === bDef.hotelRoomReserve.roomTypeName.toLowerCase()
            );
            if (rm) {
              const resvRes = await request(`/hotels/rooms/${rm.id}/reserve?count=${bDef.hotelRoomReserve.count}`, {
                method: 'PUT'
              });
              if (resvRes.ok) {
                console.log(`    ✓ Reserved ${bDef.hotelRoomReserve.count} room(s) of '${rm.roomTypeName}' in ${h.name}`);
              }
            }
          }
        }
      } else {
        console.error(`  ✗ Failed to create booking ${bDef.bookingRef}:`, createBkgRes.data);
        continue;
      }
    }

    // 2. Check and record Payment Transaction
    const checkPayRes = await request(`/payments/booking/${bDef.bookingRef}`);
    if (checkPayRes.ok && checkPayRes.data) {
      stats.paymentsExisting++;
    } else {
      const payRes = await request('/payments/process', {
        method: 'POST',
        body: JSON.stringify({
          bookingRef: bDef.bookingRef,
          customerId: bDef.customerId,
          amount: bDef.totalAmount,
          paymentMethod: bDef.paymentMethod
        })
      });
      if (payRes.ok) {
        stats.paymentsCreated++;
        const txn = payRes.data;
        console.log(`    ✓ Processed Payment: ${txn.transactionRef} for ${bDef.bookingRef} (₹${bDef.totalAmount} via ${bDef.paymentMethod})`);

        if (bDef.status === 'CANCELLED') {
          // Process refund
          await request(`/payments/refund/${txn.transactionRef}`, { method: 'POST' });
          console.log(`    ✓ Processed Refund for ${txn.transactionRef}`);
        }
      }
    }

    // 3. Check and submit Review (if defined)
    if (bDef.review) {
      const revCheckRes = await request(`/reviews/service/${bDef.serviceType}/${bDef.serviceId}`);
      const revExists = revCheckRes.ok && Array.isArray(revCheckRes.data) &&
        revCheckRes.data.some(r => r.bookingRef === bDef.bookingRef);

      if (revExists) {
        stats.reviewsExisting++;
      } else {
        const revRes = await request('/reviews', {
          method: 'POST',
          body: JSON.stringify({
            bookingRef: bDef.bookingRef,
            customerId: bDef.customerId,
            customerName: bDef.customerName,
            serviceType: bDef.serviceType,
            serviceId: bDef.serviceId,
            rating: bDef.review.rating,
            comment: bDef.review.comment
          })
        });
        if (revRes.ok) {
          stats.reviewsCreated++;
          console.log(`    ✓ Created ${bDef.review.rating}★ Review for ${bDef.bookingRef}`);
        }
      }
    }

    // 4. Check and record Notification Log (if defined)
    if (bDef.notification) {
      const notifCheckRes = await request(`/notifications/user/${encodeURIComponent(bDef.notification.recipientEmail)}`);
      const notifExists = notifCheckRes.ok && Array.isArray(notifCheckRes.data) &&
        notifCheckRes.data.some(n => n.subject === bDef.notification.subject);

      if (notifExists) {
        stats.notificationsExisting++;
      } else {
        const notifRes = await request('/notifications/send', {
          method: 'POST',
          body: JSON.stringify(bDef.notification)
        });
        if (notifRes.ok) {
          stats.notificationsCreated++;
          console.log(`    ✓ Dispatched Notification to ${bDef.notification.recipientEmail} (${bDef.notification.channel})`);
        }
      }
    }
  }

  console.log(`  Bookings, Payments & Reviews ready.`);
}

async function verifyAll() {
  console.log('\n🔍 --- [4/4] Verifying Seeded Endpoints & Application Readiness ---');

  // Verify bus search for today
  const busSearch = await request('/buses/search?origin=Bengaluru&destination=Hyderabad&date=2026-10-16');
  console.log(`  [VERIFY] Bus Search (Bengaluru ➔ Hyderabad on 2026-10-16): Found ${busSearch.ok ? busSearch.data.length : 0} schedules`);

  // Verify hotel search for Goa
  const hotelGoa = await request('/hotels/search?city=Goa');
  console.log(`  [VERIFY] Hotel Search (City: Goa): Found ${hotelGoa.ok ? hotelGoa.data.length : 0} properties`);

  // Verify hotel search for Bengaluru
  const hotelBlr = await request('/hotels/search?city=Bengaluru');
  console.log(`  [VERIFY] Hotel Search (City: Bengaluru): Found ${hotelBlr.ok ? hotelBlr.data.length : 0} properties`);

  // Verify customer bookings for John Doe (ID 4)
  const custBkg = await request('/bookings/customer/4');
  console.log(`  [VERIFY] Customer Bookings (John Doe): Found ${custBkg.ok ? custBkg.data.length : 0} bookings`);

  // Verify reviews in review service
  const reviews = await request('/reviews/admin/all');
  console.log(`  [VERIFY] Reviews: Total ${reviews.ok ? reviews.data.length : 0} customer reviews on platform`);

  // Verify notifications for John Doe
  const notifs = await request('/notifications/user/customer@traveleasy.com');
  console.log(`  [VERIFY] Notifications (customer@traveleasy.com): ${notifs.ok ? notifs.data.length : 0} logs stored`);
}

async function main() {
  console.log('================================================================');
  console.log('   TRAVEL EASY — DEMONSTRATION DATA SEEDING RUNNER');
  console.log('   Microservices Architecture & API Integration');
  console.log('   Execution Target Date: 2026-10-16');
  console.log('================================================================');

  try {
    const busData = await seedBuses();
    const hotelMap = await seedHotels();
    await seedBookingsAndTransactions(busData, hotelMap);
    await verifyAll();

    console.log('\n================================================================');
    console.log('   DATA SEEDING COMPLETED SUCCESSFULLY!');
    console.log('================================================================');
    console.log(`   Buses:          Created: ${stats.busesCreated}, Existing: ${stats.busesExisting}`);
    console.log(`   Schedules:      Created: ${stats.schedulesCreated}, Existing: ${stats.schedulesExisting}`);
    console.log(`   Hotels:         Created: ${stats.hotelsCreated}, Existing: ${stats.hotelsExisting}`);
    console.log(`   Rooms:          Created: ${stats.roomsCreated}, Existing: ${stats.roomsExisting}`);
    console.log(`   Bookings:       Created: ${stats.bookingsCreated}, Existing: ${stats.bookingsExisting}`);
    console.log(`   Payments:       Created: ${stats.paymentsCreated}, Existing: ${stats.paymentsExisting}`);
    console.log(`   Reviews:        Created: ${stats.reviewsCreated}, Existing: ${stats.reviewsExisting}`);
    console.log(`   Notifications:  Created: ${stats.notificationsCreated}, Existing: ${stats.notificationsExisting}`);
    console.log('================================================================\n');
  } catch (err) {
    console.error('Fatal error during seeding:', err);
    process.exit(1);
  }
}

main();
