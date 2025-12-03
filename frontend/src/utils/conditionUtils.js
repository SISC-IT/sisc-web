// 교차 연산자 집합
export const CROSS_SET = new Set(['CROSSES_ABOVE', 'CROSSES_BELOW']);

export function effDimension(indDef, transformValues) {
  if (!indDef) return null;
  if (indDef.transforms && transformValues) {
    for (const t of indDef.transforms) {
      if (
        t.type === 'boolean' &&
        transformValues[t.code] &&
        t.affectsDimension
      ) {
        return t.affectsDimension;
      }
    }
  }
  return indDef.dimension;
}

export function normalizeSide(dict, side) {
  if (side.type === 'indicator') {
    const def = dict.indicators.find((i) => i.code === side.code);
    const dim = effDimension(def, side.transforms);
    return {
      type: 'indicator',
      code: side.code,
      output: side.output,
      params: side.params || {},
      transforms: side.transforms || {},
      dimension: dim,
    };
  }

  if (side.type === 'price') {
    const pf = dict.priceFields.find((p) => p.code === side.field);
    return {
      type: 'price',
      field: side.field,
      dimension: pf?.dimension || null,
    };
  }

  return { type: 'const', value: Number(side.value), dimension: 'const' };
}

export function dimMeta(dict, id) {
  return dict.dimensions.find((d) => d.id === id) || null;
}
