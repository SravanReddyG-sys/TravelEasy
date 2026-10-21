const schedulesToSeed = [
  // --- 2026-10-21 (Today) ---
  {
    busId: 7,
    origin: 'Bengaluru',
    destination: 'Hyderabad',
    travelDate: '2026-10-21',
    departureTime: '21:00',
    arrivalTime: '06:30',
    fare: 1150.0,
    boardingPoints: 'Majestic (21:00), Hebbal (21:40), Kempegowda Airport Toll (22:15)',
    droppingPoints: 'Shamshabad (05:45), Gachibowli (06:15), Ameerpet (06:30)'
  },
  {
    busId: 10,
    origin: 'Bengaluru',
    destination: 'Hyderabad',
    travelDate: '2026-10-21',
    departureTime: '22:15',
    arrivalTime: '07:45',
    fare: 1050.0,
    boardingPoints: 'Kalaisipalyam (22:15), Tin Factory (22:45), Yelahanka (23:15)',
    droppingPoints: 'Kurnool Bypass (04:15), Shamshabad (07:00), MGBS (07:45)'
  },
  {
    busId: 1,
    origin: 'Bengaluru',
    destination: 'Hyderabad',
    travelDate: '2026-10-21',
    departureTime: '19:30',
    arrivalTime: '05:00',
    fare: 1250.0,
    boardingPoints: 'Electronic City (19:30), Silk Board (19:50), Majestic (20:30)',
    droppingPoints: 'Shamshabad (04:15), Mehdipatnam (04:45), Ameerpet (05:00)'
  },
  {
    busId: 2,
    origin: 'Bengaluru',
    destination: 'Hyderabad',
    travelDate: '2026-10-21',
    departureTime: '06:00',
    arrivalTime: '15:30',
    fare: 950.0,
    boardingPoints: 'Majestic (06:00), Hebbal (06:30)',
    droppingPoints: 'Shamshabad (14:45), MGBS (15:30)'
  },
  {
    busId: 3,
    origin: 'Bengaluru',
    destination: 'Hyderabad',
    travelDate: '2026-10-21',
    departureTime: '14:00',
    arrivalTime: '23:30',
    fare: 750.0,
    boardingPoints: 'Kalaisipalyam (14:00), Hebbal (14:45)',
    droppingPoints: 'Shamshabad (22:45), MGBS (23:30)'
  },
  {
    busId: 8,
    origin: 'Bengaluru',
    destination: 'Hyderabad',
    travelDate: '2026-10-21',
    departureTime: '23:30',
    arrivalTime: '08:30',
    fare: 1350.0,
    boardingPoints: 'Silk Board (23:30), Marathahalli (23:55), Tin Factory (00:20)',
    droppingPoints: 'Gachibowli (07:45), Hitec City (08:00), Kukatpally (08:30)'
  },
  // Reverse: Hyderabad -> Bengaluru on 2026-10-21
  {
    busId: 7,
    origin: 'Hyderabad',
    destination: 'Bengaluru',
    travelDate: '2026-10-21',
    departureTime: '21:30',
    arrivalTime: '07:00',
    fare: 1200.0,
    boardingPoints: 'Ameerpet (21:30), Gachibowli (22:00), Shamshabad (22:30)',
    droppingPoints: 'Hebbal (06:15), Majestic (06:45), Electronic City (07:15)'
  },
  {
    busId: 1,
    origin: 'Hyderabad',
    destination: 'Bengaluru',
    travelDate: '2026-10-21',
    departureTime: '22:30',
    arrivalTime: '07:45',
    fare: 1100.0,
    boardingPoints: 'MGBS (22:30), Shamshabad (23:15)',
    droppingPoints: 'Yelahanka (07:00), Majestic (07:45)'
  },
  // Vijayawada <-> Hyderabad on 2026-10-21
  {
    busId: 1,
    origin: 'Vijayawada',
    destination: 'Hyderabad',
    travelDate: '2026-10-21',
    departureTime: '23:00',
    arrivalTime: '05:00',
    fare: 850.0,
    boardingPoints: 'Pandit Nehru Bus Station (23:00), Benz Circle (23:20)',
    droppingPoints: 'LB Nagar (04:15), MGBS (04:45), Ameerpet (05:00)'
  },
  {
    busId: 2,
    origin: 'Vijayawada',
    destination: 'Hyderabad',
    travelDate: '2026-10-21',
    departureTime: '06:30',
    arrivalTime: '12:30',
    fare: 700.0,
    boardingPoints: 'PNBS (06:30), Ramavarappadu (06:50)',
    droppingPoints: 'LB Nagar (11:45), MGBS (12:30)'
  },
  {
    busId: 3,
    origin: 'Vijayawada',
    destination: 'Hyderabad',
    travelDate: '2026-10-21',
    departureTime: '15:00',
    arrivalTime: '21:00',
    fare: 550.0,
    boardingPoints: 'PNBS (15:00), Gollapudi (15:30)',
    droppingPoints: 'LB Nagar (20:30), MGBS (21:00)'
  },
  {
    busId: 1,
    origin: 'Hyderabad',
    destination: 'Vijayawada',
    travelDate: '2026-10-21',
    departureTime: '23:30',
    arrivalTime: '05:30',
    fare: 850.0,
    boardingPoints: 'Ameerpet (23:00), MGBS (23:30), LB Nagar (00:00)',
    droppingPoints: 'Gollapudi (04:45), PNBS (05:15), Benz Circle (05:30)'
  },
  // --- 2026-10-22 (Tomorrow) ---
  {
    busId: 7,
    origin: 'Bengaluru',
    destination: 'Hyderabad',
    travelDate: '2026-10-22',
    departureTime: '21:00',
    arrivalTime: '06:30',
    fare: 1150.0,
    boardingPoints: 'Majestic (21:00), Hebbal (21:40), Kempegowda Airport Toll (22:15)',
    droppingPoints: 'Shamshabad (05:45), Gachibowli (06:15), Ameerpet (06:30)'
  },
  {
    busId: 10,
    origin: 'Bengaluru',
    destination: 'Hyderabad',
    travelDate: '2026-10-22',
    departureTime: '22:15',
    arrivalTime: '07:45',
    fare: 1050.0,
    boardingPoints: 'Kalaisipalyam (22:15), Tin Factory (22:45), Yelahanka (23:15)',
    droppingPoints: 'Kurnool Bypass (04:15), Shamshabad (07:00), MGBS (07:45)'
  },
  {
    busId: 1,
    origin: 'Bengaluru',
    destination: 'Hyderabad',
    travelDate: '2026-10-22',
    departureTime: '20:00',
    arrivalTime: '05:30',
    fare: 1250.0,
    boardingPoints: 'Electronic City (20:00), Majestic (20:45)',
    droppingPoints: 'Shamshabad (04:45), Ameerpet (05:30)'
  },
  {
    busId: 1,
    origin: 'Vijayawada',
    destination: 'Hyderabad',
    travelDate: '2026-10-22',
    departureTime: '23:00',
    arrivalTime: '05:00',
    fare: 850.0,
    boardingPoints: 'Pandit Nehru Bus Station (23:00), Benz Circle (23:20)',
    droppingPoints: 'LB Nagar (04:15), MGBS (04:45), Ameerpet (05:00)'
  }
];

async function seed() {
  for (const s of schedulesToSeed) {
    try {
      const searchRes = await fetch(`http://localhost:8080/api/buses/search?origin=${encodeURIComponent(s.origin)}&destination=${encodeURIComponent(s.destination)}&date=${s.travelDate}`);
      const existing = await searchRes.json();
      const match = existing.find(e => e.departureTime === s.departureTime && e.busId === s.busId);
      if (match) {
        console.log(`Schedule already exists for ${s.origin} -> ${s.destination} at ${s.departureTime} on ${s.travelDate}`);
        continue;
      }
      const res = await fetch('http://localhost:8080/api/buses/schedules', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(s)
      });
      const data = await res.json();
      console.log(`Seeded schedule #${data.id} for ${s.origin} -> ${s.destination} (${s.departureTime}) on ${s.travelDate}`);
    } catch (err) {
      console.error('Failed to seed schedule:', err.message);
    }
  }
}

seed();
