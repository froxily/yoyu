CREATE TABLE rule_versions (
  effective_from date PRIMARY KEY,
  regular_window_limit_minutes integer NOT NULL CHECK (regular_window_limit_minutes > 0),
  vacation_window_limit_minutes integer NOT NULL CHECK (vacation_window_limit_minutes > 0),
  vacation_day_limit_minutes integer NOT NULL CHECK (vacation_day_limit_minutes > 0),
  source_url text NOT NULL,
  note text
);

INSERT INTO rule_versions (effective_from, regular_window_limit_minutes, vacation_window_limit_minutes,
  vacation_day_limit_minutes, source_url, note)
VALUES ('2000-01-01', 1680, 2400, 480,
  'https://www.moj.go.jp/isa/applications/procedures/nyuukokukanri07_00003.html',
  'Baseline: 28 h in any 7 days; during school long vacations 8 h per day (ISA) and 40 h in any 7 days (Labour Standards Act week).');
