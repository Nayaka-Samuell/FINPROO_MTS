CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    city VARCHAR(80) NOT NULL,
    district VARCHAR(80) NOT NULL,
    created_at BIGINT NOT NULL
);
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    unit VARCHAR(10) NOT NULL
);
CREATE TABLE listings (
    id UUID PRIMARY KEY,
    supplier_id UUID NOT NULL REFERENCES users(id),
    category_id UUID NOT NULL REFERENCES categories(id),
    title VARCHAR(160) NOT NULL,
    description VARCHAR(5000) NOT NULL,
    total_kg NUMERIC(10,2) NOT NULL CHECK (total_kg > 0),
    available_kg NUMERIC(10,2) NOT NULL CHECK (available_kg >= 0 AND available_kg <= total_kg),
    district VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE','COMPLETED'))
);
CREATE TABLE listing_photos (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listings(id),
    image_url VARCHAR(2048) NOT NULL,
    sort_order INT NOT NULL
);
CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listings(id),
    receiver_id UUID NOT NULL REFERENCES users(id),
    requested_kg NUMERIC(10,2) NOT NULL CHECK (requested_kg > 0),
    reserved_kg NUMERIC(10,2) NOT NULL CHECK (reserved_kg >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED','APPROVED','REJECTED','CANCELLED','COMPLETED')),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE TABLE handover_tokens (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL UNIQUE REFERENCES reservations(id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at BIGINT NOT NULL,
    used_at BIGINT
);
CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL UNIQUE REFERENCES reservations(id),
    actual_kg NUMERIC(10,2) NOT NULL CHECK (actual_kg > 0),
    completed_at BIGINT NOT NULL
);
CREATE TABLE reuse_feedback (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL UNIQUE REFERENCES transfers(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('USED','STORED','UNUSED')),
    reported_at BIGINT NOT NULL
);
CREATE INDEX listings_category_status ON listings(category_id, status);
CREATE INDEX listings_district_status ON listings(district, status);
CREATE INDEX reservations_receiver_status ON reservations(receiver_id, status);
CREATE INDEX reservations_listing_status ON reservations(listing_id, status);
INSERT INTO categories(id, name, unit) VALUES
('00000000-0000-0000-0000-000000000001', 'Kain Perca', 'kg'),
('00000000-0000-0000-0000-000000000002', 'Kardus Bersih', 'kg'),
('00000000-0000-0000-0000-000000000003', 'Botol Plastik', 'kg'),
('00000000-0000-0000-0000-000000000004', 'Potongan Kayu', 'kg');
