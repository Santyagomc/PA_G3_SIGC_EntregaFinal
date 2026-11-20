-- phpMyAdmin SQL Dump
-- version 5.2.0
-- https://www.phpmyadmin.net/
--
-- Servidor: 127.0.0.1
-- Tiempo de generación: 26-03-2026 a las 02:39:25
-- Versión del servidor: 10.4.24-MariaDB
-- Versión de PHP: 8.1.6

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de datos: `sigcapi`
--

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `chats`
--

CREATE TABLE `chats` (
  `id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `mensaje` tinytext NOT NULL,
  `type` enum('ASESORIA','USUARIO') NOT NULL,
  `sesion_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `documents`
--

CREATE TABLE `documents` (
  `id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_path` varchar(255) NOT NULL,
  `request_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--
-- Volcado de datos para la tabla `documents`
--

INSERT INTO `documents` (`id`, `created_at`, `file_name`, `file_path`, `request_id`) VALUES
(2, '2026-03-25 16:37:40.000000', 'diseño 6-7-07.jpg', 'C:\\Users\\PROGRAMADOR\\OneDrive - APUESTAS NACIONALES COLOMBIA SA (1)\\ImÃ¡genes\\VEHICULOS\\pqr\\diseño 6-7-07.jpg', 4),
(3, '2026-03-25 16:37:40.000000', 'Keno - Interfaz.png', 'C:\\Users\\PROGRAMADOR\\OneDrive - APUESTAS NACIONALES COLOMBIA SA (1)\\ImÃ¡genes\\VEHICULOS\\pqr\\Keno - Interfaz.png', 4);

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `requests`
--

CREATE TABLE `requests` (
  `id` bigint(20) NOT NULL,
  `city` varchar(80) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text NOT NULL,
  `document` varchar(20) NOT NULL,
  `email` varchar(150) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `phone` varchar(20) NOT NULL,
  `status` enum('CERRADO','CREADO','PENDIENTE') NOT NULL,
  `title` varchar(100) NOT NULL,
  `type` enum('PETICIÓN','QUEJA','RECLAMO') NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--
-- Volcado de datos para la tabla `requests`
--

INSERT INTO `requests` (`id`, `city`, `created_at`, `description`, `document`, `email`, `full_name`, `phone`, `status`, `title`, `type`) VALUES
(4, 'Neiva', '2026-03-25 15:00:34.000000', 'Por parte de la colaboradora que se encontraba en el punto de venta acasias en horas de las 9:00am el dia de hoy 25 de marzo del 2026 no me brindo el servicio de atención porque se encontraba en el celular.', '1077729852', 'escobarhallys@gmail.com', 'Hallys Andrea Escobar Paloma', '3126116198', 'CREADO', 'Servicio no brindado', 'QUEJA');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `sesiones`
--

CREATE TABLE `sesiones` (
  `id` bigint(20) NOT NULL,
  `closure_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `full_name` varchar(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--
-- Volcado de datos para la tabla `sesiones`
--

INSERT INTO `sesiones` (`id`, `closure_at`, `created_at`, `full_name`) VALUES
(1, '2026-03-25 20:25:59.000000', '2026-03-25 20:24:04.000000', 'Hallys Andrea Escobar'),
(2, NULL, '2026-03-25 20:26:31.000000', 'Hallys Andrea Escobar');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `users`
--

CREATE TABLE `users` (
  `id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `document` varchar(150) NOT NULL,
  `email` varchar(150) NOT NULL,
  `failed_attempts` int(11) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `lock_until` datetime(6) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `rol` varchar(255) DEFAULT NULL,
  `status` enum('ACTIVO','BLOQUEADO','INACTIVO') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--
-- Volcado de datos para la tabla `users`
--

INSERT INTO `users` (`id`, `created_at`, `document`, `email`, `failed_attempts`, `full_name`, `lock_until`, `password`, `rol`, `status`, `updated_at`) VALUES
(1, '2026-03-14 17:48:47.000000', '1077729852', 'escobarhallys@gmail.com', 0, 'Hallys Escobar', NULL, '$2a$12$4mQjk4OsgsMzADASFLjUv.7pOA5oGTaE60evKGp1ricOsbI0Sz9zG', 'CONSULTOR', 'ACTIVO', '2026-03-14 12:57:01.000000'),
(2, '2026-03-25 11:15:19.000000', '1024460334', 'escobarhallys@gmail.com', 0, 'Santiago Murcia', NULL, '$2a$12$amWNWs0HbogkyeOqJuKhme1TClMAc3OvCTwhGW1yjYQBm2baw/MTu', 'ADMIN', 'ACTIVO', '2026-03-25 11:15:19.000000');

--
-- Índices para tablas volcadas
--

--
-- Indices de la tabla `chats`
--
ALTER TABLE `chats`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_sesion_id` (`sesion_id`);

--
-- Indices de la tabla `documents`
--
ALTER TABLE `documents`
  ADD PRIMARY KEY (`id`),
  ADD KEY `FKst5u0lvv2dr7x4plrtdit0jcl` (`request_id`);

--
-- Indices de la tabla `requests`
--
ALTER TABLE `requests`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_title` (`title`),
  ADD KEY `idx_user_document` (`document`);

--
-- Indices de la tabla `sesiones`
--
ALTER TABLE `sesiones`
  ADD PRIMARY KEY (`id`);

--
-- Indices de la tabla `users`
--
ALTER TABLE `users`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_user_full_name` (`full_name`),
  ADD KEY `idx_user_document` (`document`);

--
-- AUTO_INCREMENT de las tablas volcadas
--

--
-- AUTO_INCREMENT de la tabla `chats`
--
ALTER TABLE `chats`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de la tabla `documents`
--
ALTER TABLE `documents`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

--
-- AUTO_INCREMENT de la tabla `requests`
--
ALTER TABLE `requests`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

--
-- AUTO_INCREMENT de la tabla `sesiones`
--
ALTER TABLE `sesiones`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- AUTO_INCREMENT de la tabla `users`
--
ALTER TABLE `users`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=8;

--
-- Restricciones para tablas volcadas
--

--
-- Filtros para la tabla `chats`
--
ALTER TABLE `chats`
  ADD CONSTRAINT `FKn1y62i2b1uoqf4cbfykddwos4` FOREIGN KEY (`sesion_id`) REFERENCES `sesiones` (`id`);

--
-- Filtros para la tabla `documents`
--
ALTER TABLE `documents`
  ADD CONSTRAINT `FKst5u0lvv2dr7x4plrtdit0jcl` FOREIGN KEY (`request_id`) REFERENCES `requests` (`id`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
