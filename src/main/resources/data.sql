INSERT IGNORE INTO equipo_real (id, nombre, sigla, color_principal, color_secundario) VALUES
(1, 'San Lorenzo',        'SLO', '#1A3A6B', '#E8001C'),
(2, 'Boca Juniors',       'BOC', '#003087', '#FDD116'),
(3, 'Quimsa',             'QUI', '#006341', '#FFFFFF'),
(4, 'Peñarol',            'PEN', '#000000', '#FFD700'),
(5, 'Instituto',          'INS', '#CC0000', '#FFFFFF'),
(6, 'Independiente',      'IND', '#1d223f', '#FFFFFF');

INSERT IGNORE INTO jugador_real
    (id, ges_id, nombre_completo, numero_camiseta, equipo_lnb_id,
     posicion, estado, valor_mercado_actual, valor_base, creado_en, actualizado_en) VALUES
-- San Lorenzo
(1,  150723, 'BROCAL, Agustín',    11, 1, 'ESCOLTA',   'DISPONIBLE', 7.50, 7.50, NOW(), NOW()),
(2,  379318, 'SANSIMONI, Bruno',   23, 1, 'BASE',      'DISPONIBLE', 8.00, 8.00, NOW(), NOW()),
(3,  381837, 'DREPER, Darío',       5, 1, 'PIVOT',     'DISPONIBLE', 7.00, 7.00, NOW(), NOW()),
-- Quimsa
(4,  379539, 'FERREYRA, Sebastián', 7, 3, 'ALERO',     'DISPONIBLE', 9.00, 9.00, NOW(), NOW()),
(5,  326092, 'VORHEES, Will',      32, 3, 'ALA_PIVOT', 'DISPONIBLE', 8.50, 8.50, NOW(), NOW()),
(6,  326699, 'BUENDIA, Carlos',    15, 3, 'BASE',      'DISPONIBLE', 6.50, 6.50, NOW(), NOW()),
-- Instituto
(7,  386024, 'GONZALEZ, Lucas',    10, 5, 'ESCOLTA',   'DISPONIBLE', 7.00, 7.00, NOW(), NOW()),
(8,  209522, 'OBERTO, Julián',     21, 5, 'PIVOT',     'DISPONIBLE', 6.00, 6.00, NOW(), NOW()),
(9,  209518, 'ZURSCHMITTEN, Nico',  4, 5, 'ALERO',     'DISPONIBLE', 5.50, 5.50, NOW(), NOW());

-- Equipo virtual del usuario de prueba (id=1 debe existir en tabla usuario)
-- Ejecutar manualmente en MySQL, NO en data.sql para evitar errores
-- si el usuario aún no existe al arrancar:

    INSERT IGNORE INTO director_tecnico
    (id, nombre_completo, nacionalidad, equipo_lnb_id,
     estado, creado_en, actualizado_en)
VALUES
(1, 'Gonzalo García',    'Argentina', 1, 'DISPONIBLE', NOW(), NOW()),  -- San Lorenzo
(2, 'Sebastián Ginóbili','Argentina', 2, 'DISPONIBLE', NOW(), NOW()),  -- Boca
(3, 'Diego Gutiérrez',   'Argentina', 3, 'DISPONIBLE', NOW(), NOW()),  -- Quimsa
(4, 'Martín Villareal',  'España',    4, 'DISPONIBLE', NOW(), NOW()),  -- Peñarol
(5, 'Lucas Mondelo',     'España',    5, 'DISPONIBLE', NOW(), NOW());  -- Instituto