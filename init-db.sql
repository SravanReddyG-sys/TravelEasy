-- MySQL Database Initialization Script for TravelEasy Microservices
CREATE DATABASE IF NOT EXISTS traveleasy_auth_db;
CREATE DATABASE IF NOT EXISTS traveleasy_bus_db;
CREATE DATABASE IF NOT EXISTS traveleasy_hotel_db;
CREATE DATABASE IF NOT EXISTS traveleasy_booking_db;
CREATE DATABASE IF NOT EXISTS traveleasy_payment_db;
CREATE DATABASE IF NOT EXISTS traveleasy_notification_db;
CREATE DATABASE IF NOT EXISTS traveleasy_review_db;

GRANT ALL PRIVILEGES ON *.* TO 'root'@'%';
FLUSH PRIVILEGES;
