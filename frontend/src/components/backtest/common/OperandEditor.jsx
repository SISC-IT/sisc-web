import { useEffect, useMemo } from 'react';
import styles from './ConditionCard.module.css';
import Field from './Field';

function NumberInput({ value, onChange, min, max, step = 1, placeholder }) {
  return (
    <input
      className={styles.input}
      type="number"
      value={value ?? ''}
      onChange={(e) => {
        const num = e.target.value === '' ? '' : Number(e.target.value);
        onChange(num);
      }}
      min={min ?? undefined}
      max={max ?? undefined}
      step={step}
      placeholder={placeholder}
    />
  );
}

function ParamsEditor({ schema = [], values = {}, onChange }) {
  return (
    <div className={styles.paramGrid}>
      {schema.map((p) => (
        <Field key={p.name} label={p.name}>
          <NumberInput
            value={values[p.name] ?? p.default ?? ''}
            onChange={(v) => onChange({ ...values, [p.name]: v })}
            min={p.min}
            max={p.max}
            step={p.step ?? 1}
          />
        </Field>
      ))}
    </div>
  );
}

function OutputsEditor({ outputs = [], value, onChange }) {
  useEffect(() => {
    if (!outputs || outputs.length !== 1) return;

    const fixed = outputs[0].name;
    if (value !== fixed) {
      onChange(fixed);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [outputs, value]);

  if (!outputs || outputs.length === 0) return null;

  if (outputs.length === 1) {
    const fixed = outputs[0].name;
    return (
      <Field label="output">
        <input
          className={styles.input}
          value={outputs[0].label || fixed}
          disabled
        />
      </Field>
    );
  }

  return (
    <Field label="output">
      <select
        className={styles.select}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        {outputs.map((o) => (
          <option key={o.name} value={o.name}>
            {o.label || o.name}
          </option>
        ))}
      </select>
    </Field>
  );
}

export default function OperandEditor({
  title,
  dict,
  value,
  setValue,
  indicatorFilter,
}) {
  const indicators = useMemo(() => {
    const all = dict.indicators || [];
    return indicatorFilter ? all.filter(indicatorFilter) : all;
  }, [dict, indicatorFilter]);

  const currentDef =
    (value.type === 'indicator' &&
      indicators.find((i) => i.code === value.code)) ||
    indicators[0];

  useEffect(() => {
    if (!dict) return;
    if (value.type !== 'indicator') return;
    if (value.code) return; // 이미 초기화 된 경우는 건너뜀

    const def = indicators[0];
    if (!def) return;

    const params = (def.params || []).reduce((acc, p) => {
      acc[p.name] = p.default ?? '';
      return acc;
    }, {});
    const output = def.outputs?.[0]?.name || 'value';
    const transforms = (def.transforms || []).reduce((acc, t) => {
      acc[t.code] = !!t.default;
      return acc;
    }, {});

    setValue({
      type: 'indicator',
      code: def.code,
      params,
      output,
      transforms,
    });
  }, [dict, indicators, value.type, value.code, setValue]);

  function setType(next) {
    if (next === 'indicator') {
      const def = indicators[0];
      const params = (def?.params || []).reduce((acc, p) => {
        acc[p.name] = p.default ?? '';
        return acc;
      }, {});
      const output = def?.outputs?.[0]?.name || 'value';
      const transforms = (def?.transforms || []).reduce((acc, t) => {
        acc[t.code] = !!t.default;
        return acc;
      }, {});
      setValue({
        type: 'indicator',
        code: def?.code,
        params,
        output,
        transforms,
      });
    } else if (next === 'price') {
      setValue({ type: 'price', field: dict.priceFields[0]?.code || 'Close' });
    } else {
      setValue({ type: 'const', value: '' });
    }
  }

  function setIndicator(code) {
    const def = indicators.find((i) => i.code === code);
    const params = (def?.params || []).reduce((acc, p) => {
      acc[p.name] = p.default ?? '';
      return acc;
    }, {});
    const output = def?.outputs?.[0]?.name || 'value';
    const transforms = (def?.transforms || []).reduce((acc, t) => {
      acc[t.code] = !!t.default;
      return acc;
    }, {});
    setValue({ ...value, type: 'indicator', code, params, output, transforms });
  }

  return (
    <div className={styles.side}>
      <div className={styles.sideTitle}>{title}</div>

      <Field label="타입">
        <select
          className={styles.select}
          value={value.type}
          onChange={(e) => setType(e.target.value)}
        >
          <option value="indicator">indicator</option>
          <option value="price">price</option>
          <option value="const">const</option>
        </select>
      </Field>

      {value.type === 'indicator' && currentDef && (
        <>
          <Field label="지표">
            <select
              className={styles.select}
              value={value.code || currentDef.code}
              onChange={(e) => setIndicator(e.target.value)}
            >
              {indicators.map((it) => (
                <option key={it.code} value={it.code}>
                  {it.name}
                </option>
              ))}
            </select>
          </Field>

          <OutputsEditor
            outputs={currentDef.outputs}
            value={value.output}
            onChange={(output) => setValue({ ...value, output })}
          />

          <ParamsEditor
            schema={currentDef.params}
            values={value.params || {}}
            onChange={(params) => setValue({ ...value, params })}
          />
        </>
      )}

      {value.type === 'price' && (
        <Field label="가격/원시">
          <select
            className={styles.select}
            value={value.field}
            onChange={(e) => setValue({ ...value, field: e.target.value })}
          >
            {dict.priceFields.map((p) => (
              <option key={p.code} value={p.code}>
                {p.name} ({p.code})
              </option>
            ))}
          </select>
        </Field>
      )}

      {value.type === 'const' && (
        <Field label="상수">
          <NumberInput
            value={value.value}
            onChange={(v) => setValue({ ...value, value: v })}
            placeholder={value.__constPlaceholder || ''}
          />
        </Field>
      )}
    </div>
  );
}
