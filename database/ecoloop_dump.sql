--
-- PostgreSQL database dump
--

\restrict b0TEcQb4haaPUCeMTbs0oZWHcEHQQjylS32AaZC70oAOBLPINFyRwzfLHjGxcXh

-- Dumped from database version 16.16
-- Dumped by pg_dump version 16.16

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY public.transfers DROP CONSTRAINT IF EXISTS transfers_reservation_id_fkey;
ALTER TABLE IF EXISTS ONLY public.reuse_feedback DROP CONSTRAINT IF EXISTS reuse_feedback_transfer_id_fkey;
ALTER TABLE IF EXISTS ONLY public.reservations DROP CONSTRAINT IF EXISTS reservations_receiver_id_fkey;
ALTER TABLE IF EXISTS ONLY public.reservations DROP CONSTRAINT IF EXISTS reservations_listing_id_fkey;
ALTER TABLE IF EXISTS ONLY public.listings DROP CONSTRAINT IF EXISTS listings_supplier_id_fkey;
ALTER TABLE IF EXISTS ONLY public.listings DROP CONSTRAINT IF EXISTS listings_category_id_fkey;
ALTER TABLE IF EXISTS ONLY public.listing_photos DROP CONSTRAINT IF EXISTS listing_photos_listing_id_fkey;
ALTER TABLE IF EXISTS ONLY public.handover_tokens DROP CONSTRAINT IF EXISTS handover_tokens_reservation_id_fkey;
DROP INDEX IF EXISTS public.reservations_receiver_status;
DROP INDEX IF EXISTS public.reservations_listing_status;
DROP INDEX IF EXISTS public.listings_district_status;
DROP INDEX IF EXISTS public.listings_category_status;
DROP INDEX IF EXISTS public.flyway_schema_history_s_idx;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE IF EXISTS ONLY public.transfers DROP CONSTRAINT IF EXISTS transfers_reservation_id_key;
ALTER TABLE IF EXISTS ONLY public.transfers DROP CONSTRAINT IF EXISTS transfers_pkey;
ALTER TABLE IF EXISTS ONLY public.reuse_feedback DROP CONSTRAINT IF EXISTS reuse_feedback_transfer_id_key;
ALTER TABLE IF EXISTS ONLY public.reuse_feedback DROP CONSTRAINT IF EXISTS reuse_feedback_pkey;
ALTER TABLE IF EXISTS ONLY public.reservations DROP CONSTRAINT IF EXISTS reservations_pkey;
ALTER TABLE IF EXISTS ONLY public.listings DROP CONSTRAINT IF EXISTS listings_pkey;
ALTER TABLE IF EXISTS ONLY public.listing_photos DROP CONSTRAINT IF EXISTS listing_photos_pkey;
ALTER TABLE IF EXISTS ONLY public.handover_tokens DROP CONSTRAINT IF EXISTS handover_tokens_reservation_id_key;
ALTER TABLE IF EXISTS ONLY public.handover_tokens DROP CONSTRAINT IF EXISTS handover_tokens_pkey;
ALTER TABLE IF EXISTS ONLY public.flyway_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
ALTER TABLE IF EXISTS ONLY public.categories DROP CONSTRAINT IF EXISTS categories_pkey;
ALTER TABLE IF EXISTS ONLY public.categories DROP CONSTRAINT IF EXISTS categories_name_key;
DROP TABLE IF EXISTS public.users;
DROP TABLE IF EXISTS public.transfers;
DROP TABLE IF EXISTS public.reuse_feedback;
DROP TABLE IF EXISTS public.reservations;
DROP TABLE IF EXISTS public.listings;
DROP TABLE IF EXISTS public.listing_photos;
DROP TABLE IF EXISTS public.handover_tokens;
DROP TABLE IF EXISTS public.flyway_schema_history;
DROP TABLE IF EXISTS public.categories;
SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    id uuid NOT NULL,
    name character varying(100) NOT NULL,
    unit character varying(10) NOT NULL
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: handover_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.handover_tokens (
    id uuid NOT NULL,
    reservation_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at bigint NOT NULL,
    used_at bigint
);


--
-- Name: listing_photos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.listing_photos (
    id uuid NOT NULL,
    listing_id uuid NOT NULL,
    image_url character varying(2048) NOT NULL,
    sort_order integer NOT NULL
);


--
-- Name: listings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.listings (
    id uuid NOT NULL,
    supplier_id uuid NOT NULL,
    category_id uuid NOT NULL,
    title character varying(160) NOT NULL,
    description character varying(5000) NOT NULL,
    total_kg numeric(10,2) NOT NULL,
    available_kg numeric(10,2) NOT NULL,
    district character varying(80) NOT NULL,
    status character varying(20) NOT NULL,
    CONSTRAINT listings_check CHECK (((available_kg >= (0)::numeric) AND (available_kg <= total_kg))),
    CONSTRAINT listings_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'COMPLETED'::character varying])::text[]))),
    CONSTRAINT listings_total_kg_check CHECK ((total_kg > (0)::numeric))
);


--
-- Name: reservations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservations (
    id uuid NOT NULL,
    listing_id uuid NOT NULL,
    receiver_id uuid NOT NULL,
    requested_kg numeric(10,2) NOT NULL,
    reserved_kg numeric(10,2) NOT NULL,
    status character varying(20) NOT NULL,
    created_at bigint NOT NULL,
    updated_at bigint NOT NULL,
    CONSTRAINT reservations_requested_kg_check CHECK ((requested_kg > (0)::numeric)),
    CONSTRAINT reservations_reserved_kg_check CHECK ((reserved_kg >= (0)::numeric)),
    CONSTRAINT reservations_status_check CHECK (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying, 'COMPLETED'::character varying])::text[])))
);


--
-- Name: reuse_feedback; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reuse_feedback (
    id uuid NOT NULL,
    transfer_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    reported_at bigint NOT NULL,
    CONSTRAINT reuse_feedback_status_check CHECK (((status)::text = ANY ((ARRAY['USED'::character varying, 'STORED'::character varying, 'UNUSED'::character varying])::text[])))
);


--
-- Name: transfers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transfers (
    id uuid NOT NULL,
    reservation_id uuid NOT NULL,
    actual_kg numeric(10,2) NOT NULL,
    completed_at bigint NOT NULL,
    CONSTRAINT transfers_actual_kg_check CHECK ((actual_kg > (0)::numeric))
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    email character varying(254) NOT NULL,
    password_hash character varying(100) NOT NULL,
    city character varying(80) NOT NULL,
    district character varying(80) NOT NULL,
    created_at bigint NOT NULL
);


--
-- Data for Name: categories; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.categories (id, name, unit) FROM stdin;
00000000-0000-0000-0000-000000000001	Kain Perca	kg
00000000-0000-0000-0000-000000000002	Kardus Bersih	kg
00000000-0000-0000-0000-000000000003	Botol Plastik	kg
00000000-0000-0000-0000-000000000004	Potongan Kayu	kg
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	initial schema	SQL	V1__initial_schema.sql	2004101264	ecoloop	2026-11-17 23:19:03.830637	92	t
\.


--
-- Data for Name: handover_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.handover_tokens (id, reservation_id, token_hash, expires_at, used_at) FROM stdin;
c3e99613-5456-4244-a427-1e46df8b9e90	d7737ce5-bafb-41d3-afe6-e0e594dc3fe5	6a78456068e42be0f49a14243db47114c3746b4020d1aae0e7b6716cd91fe023	1794933274918	1794932374965
\.


--
-- Data for Name: listing_photos; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.listing_photos (id, listing_id, image_url, sort_order) FROM stdin;
\.


--
-- Data for Name: listings; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.listings (id, supplier_id, category_id, title, description, total_kg, available_kg, district, status) FROM stdin;
cbc38646-abd7-4f85-9890-c236a0598688	ff238f96-1d9b-4eb2-9a12-ee2eedfdd27c	00000000-0000-0000-0000-000000000001	Perca denim biru	Potongan denim ukuran 10-40 cm, tanpa kancing logam.	6.00	6.00	Jakarta Selatan	ACTIVE
5db31231-5e91-4311-b479-90f4fc51b997	ff238f96-1d9b-4eb2-9a12-ee2eedfdd27c	00000000-0000-0000-0000-000000000002	Kardus packing double wall	Kardus bekas kiriman bahan, kokoh, sudah dilipat rapi.	20.00	20.00	Jakarta Selatan	ACTIVE
8f0127e0-70df-4d1f-8ccd-9aee0a3990b4	c3c7231a-a2d0-40f7-83ca-c7ee79049d1b	00000000-0000-0000-0000-000000000003	Botol PET 600 ml bersih	Botol air mineral bekas acara komunitas, sudah dicuci dan tanpa label.	8.75	8.75	Jakarta Timur	ACTIVE
2043e82f-0ce8-45ef-ad4c-b07a55ad0b49	c3c7231a-a2d0-40f7-83ca-c7ee79049d1b	00000000-0000-0000-0000-000000000004	Potongan kayu pinus palet	Sisa bongkar palet, paku sudah dicabut. Panjang 30-80 cm.	15.00	15.00	Jakarta Timur	ACTIVE
51822e4d-2b02-4e35-80ea-d023374b6064	ff238f96-1d9b-4eb2-9a12-ee2eedfdd27c	00000000-0000-0000-0000-000000000003	Botol plastik HDPE warna	Botol bekas sabun cair yang sudah dibilas, untuk ecobrick atau kerajinan.	4.20	4.20	Jakarta Pusat	ACTIVE
5e5d2c24-e2dc-4a7f-be15-0b79c95b9d9d	ff238f96-1d9b-4eb2-9a12-ee2eedfdd27c	00000000-0000-0000-0000-000000000001	Kain perca katun warna-warni	Sisa potongan katun dari produksi kemeja, bersih dan kering. Cocok untuk patchwork atau keset.	12.50	8.70	Jakarta Selatan	ACTIVE
\.


--
-- Data for Name: reservations; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.reservations (id, listing_id, receiver_id, requested_kg, reserved_kg, status, created_at, updated_at) FROM stdin;
d7737ce5-bafb-41d3-afe6-e0e594dc3fe5	5e5d2c24-e2dc-4a7f-be15-0b79c95b9d9d	c3c7231a-a2d0-40f7-83ca-c7ee79049d1b	4.00	4.00	COMPLETED	1794932374849	1794932374969
4f72e595-bcb1-4d7e-b55c-48fb1a9f9b50	5db31231-5e91-4311-b479-90f4fc51b997	c3c7231a-a2d0-40f7-83ca-c7ee79049d1b	5.00	0.00	REQUESTED	1794932374991	1794932374991
\.


--
-- Data for Name: reuse_feedback; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.reuse_feedback (id, transfer_id, status, reported_at) FROM stdin;
\.


--
-- Data for Name: transfers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.transfers (id, reservation_id, actual_kg, completed_at) FROM stdin;
f810e3ef-ecd1-4cbf-90ff-6a90d9e2ef2f	d7737ce5-bafb-41d3-afe6-e0e594dc3fe5	3.80	1794932374967
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (id, name, email, password_hash, city, district, created_at) FROM stdin;
ff238f96-1d9b-4eb2-9a12-ee2eedfdd27c	Sari Konveksi	sari@ecoloop.demo	$2a$12$vljRbtHTZroawGz4cGRhHu8qK/ZpYQ2v.42/qnqDIymRPNc..K5zG	DKI Jakarta	Jakarta Selatan	1794932374199
c3c7231a-a2d0-40f7-83ca-c7ee79049d1b	Budi Kriya	budi@ecoloop.demo	$2a$12$uhYUSKwyRPgJsSqoAaxcA..K81dTjwhTIgZB9rmEVduwGA1iKicri	DKI Jakarta	Jakarta Timur	1794932374564
\.


--
-- Name: categories categories_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_name_key UNIQUE (name);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: handover_tokens handover_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handover_tokens
    ADD CONSTRAINT handover_tokens_pkey PRIMARY KEY (id);


--
-- Name: handover_tokens handover_tokens_reservation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handover_tokens
    ADD CONSTRAINT handover_tokens_reservation_id_key UNIQUE (reservation_id);


--
-- Name: listing_photos listing_photos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listing_photos
    ADD CONSTRAINT listing_photos_pkey PRIMARY KEY (id);


--
-- Name: listings listings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listings
    ADD CONSTRAINT listings_pkey PRIMARY KEY (id);


--
-- Name: reservations reservations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_pkey PRIMARY KEY (id);


--
-- Name: reuse_feedback reuse_feedback_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reuse_feedback
    ADD CONSTRAINT reuse_feedback_pkey PRIMARY KEY (id);


--
-- Name: reuse_feedback reuse_feedback_transfer_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reuse_feedback
    ADD CONSTRAINT reuse_feedback_transfer_id_key UNIQUE (transfer_id);


--
-- Name: transfers transfers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT transfers_pkey PRIMARY KEY (id);


--
-- Name: transfers transfers_reservation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT transfers_reservation_id_key UNIQUE (reservation_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: listings_category_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX listings_category_status ON public.listings USING btree (category_id, status);


--
-- Name: listings_district_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX listings_district_status ON public.listings USING btree (district, status);


--
-- Name: reservations_listing_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX reservations_listing_status ON public.reservations USING btree (listing_id, status);


--
-- Name: reservations_receiver_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX reservations_receiver_status ON public.reservations USING btree (receiver_id, status);


--
-- Name: handover_tokens handover_tokens_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handover_tokens
    ADD CONSTRAINT handover_tokens_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations(id);


--
-- Name: listing_photos listing_photos_listing_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listing_photos
    ADD CONSTRAINT listing_photos_listing_id_fkey FOREIGN KEY (listing_id) REFERENCES public.listings(id);


--
-- Name: listings listings_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listings
    ADD CONSTRAINT listings_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id);


--
-- Name: listings listings_supplier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listings
    ADD CONSTRAINT listings_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.users(id);


--
-- Name: reservations reservations_listing_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_listing_id_fkey FOREIGN KEY (listing_id) REFERENCES public.listings(id);


--
-- Name: reservations reservations_receiver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_receiver_id_fkey FOREIGN KEY (receiver_id) REFERENCES public.users(id);


--
-- Name: reuse_feedback reuse_feedback_transfer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reuse_feedback
    ADD CONSTRAINT reuse_feedback_transfer_id_fkey FOREIGN KEY (transfer_id) REFERENCES public.transfers(id);


--
-- Name: transfers transfers_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT transfers_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations(id);


--
-- PostgreSQL database dump complete
--

\unrestrict b0TEcQb4haaPUCeMTbs0oZWHcEHQQjylS32AaZC70oAOBLPINFyRwzfLHjGxcXh

