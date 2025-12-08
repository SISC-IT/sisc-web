import { useState, useEffect, useMemo } from 'react';
import { fetchDictionary } from '../api/dictionary.mock';
import { normalizeSide, CROSS_SET } from '../utils/conditionUtils';

// 서버로 보낼 데이터 형식으로 변환하는 헬퍼 함수
function toServerOperand(side) {
  if (!side) return null;
  if (side.type === 'indicator') {
    return {
      type: 'indicator',
      indicatorCode: side.code,
      output: side.output,
      params: Object.fromEntries(
        Object.entries(side.params || {}).map(([k, v]) => [k, Number(v)])
      ),
    };
  }
  if (side.type === 'price') {
    return { type: 'price', priceField: side.field };
  }
  return { type: 'const', constantValue: Number(side.value) };
}

export function useConditionLogic(value, onChange) {
  const [dict, setDict] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  const [left, setLeft] = useState(value?.left || { type: 'indicator' });
  const [right, setRight] = useState(value?.right || { type: 'indicator' });
  const [operator, setOperator] = useState(value?.operator || 'GT');

  // 1. 데이터 사전 로딩
  useEffect(() => {
    fetchDictionary().then((d) => {
      setDict(d);
      setIsLoading(false);
    });
  }, []);

  // 2. 파생 상태 계산 (useMemo)
  const leftNorm = useMemo(
    () => (dict ? normalizeSide(dict, left) : null),
    [dict, left]
  );
  const rightNorm = useMemo(
    () => (dict ? normalizeSide(dict, right) : null),
    [dict, right]
  );
  const leftDim = leftNorm?.dimension || null;
  const rightDim = rightNorm?.dimension || null;

  const rightAllow = useMemo(() => {
    if (!dict || !leftDim)
      return { types: ['indicator', 'price', 'const'], indDims: [] };
    const compat = dict.dimensionCompatibility.find(
      (x) => x.leftDimension === leftDim
    );
    return {
      types: (compat?.allowRightTypes || []).slice(),
      indDims: (compat?.allowIndicatorDimensions || []).slice(),
    };
  }, [dict, leftDim]);
  const rightIndicatorFilter = useMemo(() => {
    if (!dict || rightAllow.indDims.length === 0) return undefined;
    return (iDef) => rightAllow.indDims.includes(iDef.dimension);
  }, [dict, rightAllow]);

  const operatorOptions = useMemo(() => {
    if (!dict || !leftDim || !rightDim)
      return ['GT', 'GTE', 'LT', 'LTE', 'EQ', 'NEQ'];
    const getOps = (d) =>
      dict.dimensionAllowedOperators.find((x) => x.dimension === d)
        ?.operators || [];
    let ops = getOps(leftDim).filter((c) => getOps(rightDim).includes(c));
    if (
      left.type === 'const' ||
      right.type === 'const' ||
      leftDim !== rightDim
    ) {
      ops = ops.filter((c) => !CROSS_SET.has(c));
    }
    return ops.length ? ops : ['GT', 'GTE', 'LT', 'LTE', 'EQ', 'NEQ'];
  }, [dict, leftDim, rightDim, left.type, right.type]);

  const opLabel = useMemo(() => {
    if (!dict) return (code) => code;
    const operatorMap = new Map(
      dict.operators.map((op) => [op.code, op.label])
    );
    return (code) => operatorMap.get(code) || code;
  }, [dict]);

  // 3. 상태 자동 보정 (useEffect)
  useEffect(() => {
    if (!dict || !rightAllow.types.includes(right.type)) {
      // ... (기존 ConditionCard의 우항 타입 자동 보정 로직과 동일)
    }
  }, [dict, rightAllow, right.type]);

  useEffect(() => {
    if (!operatorOptions.includes(operator)) {
      setOperator(operatorOptions[0] || 'GT');
    }
  }, [operatorOptions, operator]);

  // 4. 부모 컴포넌트로 변경사항 전파
  useEffect(() => {
    if (!onChange || isLoading) return;
    const conditionForServer = {
      leftOperand: toServerOperand(left),
      operator,
      rightOperand: toServerOperand(right),
      isAbsolute: right?.type === 'const',
    };
    onChange(conditionForServer);
  }, [left, operator, right, onChange, isLoading]);

  return {
    isLoading,
    dict,
    left,
    setLeft,
    right,
    setRight,
    operator,
    setOperator,
    rightIndicatorFilter,
    operatorOptions,
    opLabel,
  };
}
