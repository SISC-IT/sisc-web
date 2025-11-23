import { useEffect, useMemo, useState } from 'react';
import styles from './ConditionCard.module.css';
import OperandEditor from './OperandEditor';
import { fetchDictionary } from '../../../api/dictionary.mock';
import { CROSS_SET, normalizeSide } from '../../../utils/conditionUtils';

const ConditionCard = ({ value, onChange, onRemove }) => {
  const [dict, setDict] = useState(null);

  const [left, setLeft] = useState(value?.left || { type: 'indicator' });
  const [right, setRight] = useState(value?.right || { type: 'indicator' });
  const [operator, setOperator] = useState(value?.operator || 'GT');

  useEffect(() => {
    fetchDictionary().then(setDict);
  }, []);

  // 좌/우 차원 계산(Transform 반영)
  const leftNorm = useMemo(
    () => (dict ? normalizeSide(dict, left) : null),
    [dict, left]
  );
  const rightNorm = useMemo(
    () => (dict ? normalizeSide(dict, right) : null),
    [dict, right]
  );
  const leftDim = leftNorm?.dimension || null;
  const rightDim = rightNorm?.dimension || null; // 우항 허용 타입/지표 차원

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

  // 우항 지표 필터
  const rightIndicatorFilter = useMemo(() => {
    // 1) dict가 없거나
    // 2) 아직 허용되는 indicator dimension 정보가 없다면
    //    → 우항도 일단 전체 indicator를 보게 둔다(필터 X)
    if (!dict || rightAllow.indDims.length === 0) return undefined;

    // 허용 dimension이 결정된 뒤부터 필터 적용
    return (iDef) => rightAllow.indDims.includes(iDef.dimension);
  }, [dict, rightAllow]);

  // 연산자 후보 = 좌/우 차원 허용 연산자 교집합 (교차 예외 제거)
  const operatorOptions = useMemo(() => {
    if (!dict || !leftDim || !rightDim)
      return ['GT', 'GTE', 'LT', 'LTE', 'EQ', 'NEQ'];
    const getOps = (d) =>
      dict.dimensionAllowedOperators.find((x) => x.dimension === d)
        ?.operators || [];
    let ops = getOps(leftDim).filter((c) => getOps(rightDim).includes(c));

    // const가 끼거나 서로 차원이 다르면 crosses 제거
    if (
      left.type === 'const' ||
      right.type === 'const' ||
      leftDim !== rightDim
    ) {
      ops = ops.filter((c) => !CROSS_SET.has(c));
    }

    return ops.length ? ops : ['GT', 'GTE', 'LT', 'LTE', 'EQ', 'NEQ'];
  }, [dict, leftDim, rightDim, left.type, right.type]);

  // 우항 타입 자동 보정
  useEffect(() => {
    if (!dict) return;

    if (!rightAllow.types.includes(right.type)) {
      const nextType = rightAllow.types[0] || 'const';

      if (nextType === 'indicator') {
        const first = dict.indicators.find((i) =>
          rightAllow.indDims.includes(i.dimension)
        );
        const params = (first?.params || []).reduce((acc, p) => {
          acc[p.name] = p.default ?? '';
          return acc;
        }, {});
        const output = first?.outputs?.[0]?.name || 'value';
        const transforms = (first?.transforms || []).reduce((acc, t) => {
          acc[t.code] = !!t.default;
          return acc;
        }, {});

        setRight({
          type: 'indicator',
          code: first?.code,
          params,
          output,
          transforms,
        });
      } else if (nextType === 'price') {
        setRight({ type: 'price', field: dict.priceFields[0]?.code });
      } else {
        setRight({ type: 'const', value: '' });
      }
    }
  }, [dict, rightAllow, right.type]);

  // 연산자 자동 보정
  useEffect(() => {
    if (!operatorOptions.includes(operator)) {
      setOperator(operatorOptions[0] || 'GT');
    }
  }, [operatorOptions, operator]);

  // 좌항 transform에 따른 우항 상수 placeholder/힌트
  useEffect(() => {
    if (!dict) return;
    const placeholder = '';

    if (right.type === 'const') {
      setRight((r) => ({ ...r, __constPlaceholder: placeholder }));
    }
  }, [dict, left, right.type]);

  // 상위에 변경 전달
  useEffect(() => {
    if (!onChange) return;
    const conditionForServer = {
      leftOperand: toServerOperand(left),
      operator,
      rightOperand: toServerOperand(right),
      isAbsolute: right?.type === 'const',
    };
    onChange(conditionForServer);
  }, [left, operator, right]);

  if (!dict) return null;
  const opLabel = (code) =>
    dict.operators.find((o) => o.code === code)?.label || code;

  function toServerOperand(side) {
    if (!side) return null;
    if (side.type === 'indicator') {
      return {
        type: 'indicator',
        indicatorCode: side.code, // 기존 UI state의 code
        output: side.output,
        params: Object.fromEntries(
          Object.entries(side.params || {}).map(([k, v]) => [k, Number(v)])
        ),
      };
    }
    if (side.type === 'price') {
      return {
        type: 'price',
        priceField: side.field, // UI에서는 field로 관리, 서버는 priceField
      };
    }

    // const
    return { type: 'const', constantValue: Number(side.value) };
  }

  return (
    <div className={styles.card}>
      <div className={styles.cardInner}>
        <OperandEditor
          title="좌항"
          dict={dict}
          value={left}
          setValue={setLeft}
          indicatorFilter={undefined}
        />

        <div className={styles.operatorBox}>
          <span className={styles.fieldLabel}>연산자</span>
          <select
            className={styles.select}
            value={operator}
            onChange={(e) => setOperator(e.target.value)}
          >
            {operatorOptions.map((code) => (
              <option key={code} value={code}>
                {opLabel(code)}
              </option>
            ))}
          </select>
        </div>

        {/* 우항 + 삭제 버튼을 한 줄로 */}
        <div className={styles.rightWithRemove}>
          <OperandEditor
            title="우항"
            dict={dict}
            value={right}
            setValue={setRight}
            indicatorFilter={rightIndicatorFilter}
          />

          <button
            type="button"
            className={styles.removeBtn}
            onClick={onRemove}
            aria-label="delete"
          >
            &times;
          </button>
        </div>
      </div>
    </div>
  );
};

export default ConditionCard;
