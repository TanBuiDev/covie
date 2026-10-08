"use client";

import { forwardRef, useState, type InputHTMLAttributes } from "react";
import { Eye, EyeSlash } from "@phosphor-icons/react";
import styles from "./password-input.module.css";

export interface PasswordInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "type"> {
  error?: boolean;
}

export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(
  function PasswordInput({ className, error, ...props }, ref) {
    const [visible, setVisible] = useState(false);

    return (
      <div className={`${styles.wrapper} ${error ? styles.hasError : ""} ${className ?? ""}`.trim()}>
        <input
          ref={ref}
          type={visible ? "text" : "password"}
          className={styles.input}
          {...props}
        />
        <button
          type="button"
          className={styles.toggle}
          onClick={() => setVisible((prev) => !prev)}
          aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
          title={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
        >
          {visible ? <EyeSlash size={20} weight="bold" /> : <Eye size={20} weight="bold" />}
        </button>
      </div>
    );
  }
);
