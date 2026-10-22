export interface DestinationAttraction {
  name: string;
  category: string;
  image: string;
}

export interface Destination {
  id: string;
  name: string;
  tags: string;
  category: string;
  description: string;
  image: string;
  heroImage: string;
  bestTime: string;
  travelOptions: string;
  attractions: DestinationAttraction[];
}
