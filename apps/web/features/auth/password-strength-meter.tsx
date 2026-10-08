"use client";

import { useMemo } from "react";
import { Check, Dot } from "@phosphor-icons/react";
import styles from "./password-strength-meter.module.css";

interface PasswordStrengthMeterProps {
  password: string;
}

interface Criterion {
  id: string;
  label: string;
  met: boolean;
}

export function PasswordStrengthMeter({ password }: PasswordStrengthMeterProps) {
  const criteria: Criterion[] = useMemo(() => {
    return [
      {
        id: "length",
        label: "Tối thiểu 10 ký tự",
        met: password.length >= 10,
      },
      {
        id: "case",
        label: "Chữ hoa & chữ thường (a-z, A-Z)",
        met: /[a-z]/.test(password) && /[A-Z]/.test(password),
      },
      {
        id: "number",
        label: "Ít nhất 1 chữ số (0-9)",
        met: /\d/.test(password),
      },
      {
        id: "symbol",
        label: "Ký tự đặc biệt (!@#$%^&*...)",
        met: /[^a-zA-Z0-9]/.test(password),
      },
    ];
  }, [password]);

  const score = useMemo(() => {
    if (!password) return 0;
    return criteria.filter((c) => c.met).length;
  }, [criteria, password]);

  const strengthMeta = useMemo(() => {
    switch (score) {
      case 1:
        return { label: "Yếu", colorClass: styles.strengthWeak };
      case 2:
        return { label: "Trung bình", colorClass: styles.strengthMedium };
      case 3:
        return { label: "Khá mạnh", colorClass: styles.strengthGood };
      case 4:
        return { label: "Rất an toàn", colorClass: styles.strengthStrong };
      default:
        return { label: "Chưa đủ điều kiện", colorClass: styles.strengthNone };
    }
  }, [score]);

  if (!password) {
    return (
      <div className={styles.container}>
        <div className={styles.checklist}>
          {criteria.map((c) => (
            <div key={c.id} className={styles.checkItem}>
              <Dot size={16} weight="bold" className={styles.dotIcon} />
              <span>{c.label}</span>
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container} aria-live="polite">
      <div className={styles.meterHeader}>
        <span className={styles.meterTitle}>Độ mạnh mật khẩu:</span>
        <span className={`${styles.meterBadge} ${strengthMeta.colorClass}`}>
          {strengthMeta.label}
        </span>
      </div>

      <div className={styles.bars} role="progressbar" aria-valuenow={score} aria-valuemin={0} aria-valuemax={4}>
        {[1, 2, 3, 4].map((step) => {
          const active = step <= score;
          return (
            <div
              key={step}
              className={`${styles.bar} ${active ? `${styles.barActive} ${strengthMeta.colorClass}` : ""}`}
            />
          );
        })}
      </div>

      <div className={styles.checklist}>
        {criteria.map((c) => (
          <div
            key={c.id}
            className={`${styles.checkItem} ${c.met ? styles.checkItemMet : styles.checkItemUnmet}`}
          >
            {c.met ? (
              <Check size={14} weight="bold" className={styles.checkIcon} />
            ) : (
              <Dot size={16} weight="bold" className={styles.dotIcon} />
            )}
            <span>{c.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
