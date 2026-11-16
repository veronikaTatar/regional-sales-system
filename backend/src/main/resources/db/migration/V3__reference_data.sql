-- Справочные данные. Коммерческие и статистические значения не имитируются.
INSERT INTO regions(code, name, active) VALUES
('BY-BR', 'Брестская область', TRUE),
('BY-VI', 'Витебская область', TRUE),
('BY-HO', 'Гомельская область', TRUE),
('BY-HR', 'Гродненская область', TRUE),
('BY-MI', 'Минская область', TRUE),
('BY-MA', 'Могилёвская область', TRUE),
('BY-HM', 'г. Минск', TRUE);

INSERT INTO statistical_indicators(code, name, unit, periodicity) VALUES
('AVERAGE_SALARY', 'Средняя номинальная начисленная заработная плата', 'BYN', 'MONTH'),
('RETAIL_TURNOVER', 'Розничный товарооборот', 'BYN', 'MONTH'),
('POPULATION', 'Численность населения', 'человек', 'YEAR');
