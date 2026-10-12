"use client";

import {
  useState,
  useRef,
  useEffect,
  useId,
  type ReactNode,
  type KeyboardEvent,
} from "react";
import { CaretDown, Check } from "@phosphor-icons/react";
import styles from "./select-dropdown.module.css";

export type DropdownOption<T extends string | number = string | number> = {
  value: T;
  label: string;
  icon?: ReactNode;
  hint?: string;
  disabled?: boolean;
};

export interface SelectDropdownProps<T extends string | number = string | number> {
  name?: string;
  label?: string;
  "aria-label"?: string;
  placeholder?: string;
  value?: T;
  defaultValue?: T;
  options: DropdownOption<T>[];
  onChange?: (value: T) => void;
  disabled?: boolean;
  required?: boolean;
  error?: string;
  radius?: "md" | "lg" | "full";
  size?: "sm" | "md" | "lg";
  className?: string;
  id?: string;
}

export function SelectDropdown<T extends string | number = string | number>({
  name,
  label,
  "aria-label": ariaLabel,
  placeholder = "Chọn một mục...",
  value: controlledValue,
  defaultValue,
  options,
  onChange,
  disabled = false,
  required = false,
  error,
  radius = "md",
  size = "md",
  className = "",
  id,
}: SelectDropdownProps<T>) {
  const generatedId = useId();
  const componentId = id ?? generatedId;
  const listboxId = `${componentId}-listbox`;

  const [internalValue, setInternalValue] = useState<T | undefined>(
    defaultValue ?? controlledValue
  );
  const [isOpen, setIsOpen] = useState(false);
  const [focusedIndex, setFocusedIndex] = useState(-1);

  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listboxRef = useRef<HTMLDivElement>(null);

  // Sync controlled value if provided
  const activeValue = controlledValue !== undefined ? controlledValue : internalValue;
  const selectedOption = options.find((opt) => opt.value === activeValue);

  // Close when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isOpen]);

  // Handle synthetic/DOM change events on the trigger (for testing-library fireEvent.change compatibility)
  useEffect(() => {
    const trigger = triggerRef.current;
    if (!trigger) return;
    const handleNativeChange = (e: Event) => {
      const target = e.target as HTMLButtonElement | null;
      const rawVal = target?.value;
      const matchingOption = options.find((opt) => String(opt.value) === String(rawVal));
      const nextVal = matchingOption ? (matchingOption.value as T) : (rawVal as unknown as T);
      if (controlledValue === undefined) {
        setInternalValue(nextVal);
      }
      onChange?.(nextVal);
    };
    trigger.addEventListener("change", handleNativeChange);
    return () => {
      trigger.removeEventListener("change", handleNativeChange);
    };
  }, [options, controlledValue, onChange]);

  // Handle selection
  function handleSelect(option: DropdownOption<T>) {
    if (option.disabled) return;
    if (controlledValue === undefined) {
      setInternalValue(option.value);
    }
    onChange?.(option.value);
    setIsOpen(false);
    triggerRef.current?.focus();
  }

  // Keyboard navigation
  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (disabled) return;

    if (event.key === "Enter" || event.key === " " || event.key === "ArrowDown") {
      event.preventDefault();
      if (!isOpen) {
        setIsOpen(true);
        const currentIndex = options.findIndex((opt) => opt.value === activeValue);
        setFocusedIndex(currentIndex >= 0 ? currentIndex : 0);
      } else if (event.key === "ArrowDown") {
        setFocusedIndex((prev) => (prev < options.length - 1 ? prev + 1 : 0));
      }
    } else if (event.key === "ArrowUp" && isOpen) {
      event.preventDefault();
      setFocusedIndex((prev) => (prev > 0 ? prev - 1 : options.length - 1));
    } else if (event.key === "Escape" && isOpen) {
      event.preventDefault();
      setIsOpen(false);
      triggerRef.current?.focus();
    }
  }

  function handleMenuKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setFocusedIndex((prev) => (prev < options.length - 1 ? prev + 1 : 0));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setFocusedIndex((prev) => (prev > 0 ? prev - 1 : options.length - 1));
    } else if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      if (focusedIndex >= 0 && focusedIndex < options.length) {
        const target = options[focusedIndex];
        if (target && !target.disabled) {
          handleSelect(target);
        }
      }
    } else if (event.key === "Escape") {
      event.preventDefault();
      setIsOpen(false);
      triggerRef.current?.focus();
    }
  }

  // Radius class lookup
  const radiusClass =
    radius === "full"
      ? styles.radiusFull
      : radius === "lg"
      ? styles.radiusLg
      : styles.radiusMd;

  // Size class lookup
  const sizeClass =
    size === "sm" ? styles.sizeSm : size === "lg" ? styles.sizeLg : styles.sizeMd;

  return (
    <div
      ref={containerRef}
      className={`${styles.dropdownContainer} ${className}`}
    >
      {/* Hidden input for standard HTML form compatibility */}
      {name && (
        <input
          type="hidden"
          name={name}
          aria-hidden="true"
          value={activeValue !== undefined ? String(activeValue) : ""}
        />
      )}

      {/* Optional Label */}
      {label && (
        <label htmlFor={componentId} className={styles.label}>
          <span>{label}</span>
          {required && <span className={styles.requiredStar} aria-hidden="true">*</span>}
        </label>
      )}

      {/* Dropdown Trigger */}
      <button
        ref={triggerRef}
        id={componentId}
        type="button"
        role="combobox"
        disabled={disabled}
        aria-label={ariaLabel ?? label}
        value={activeValue !== undefined ? String(activeValue) : ""}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-controls={listboxId}
        onClick={() => {
          if (!disabled) {
            setIsOpen((prev) => !prev);
            if (!isOpen) {
              const currentIndex = options.findIndex((opt) => opt.value === activeValue);
              setFocusedIndex(currentIndex >= 0 ? currentIndex : 0);
            }
          }
        }}
        onKeyDown={handleKeyDown}
        onChange={(event) => {
          const rawVal = (event.target as HTMLButtonElement).value;
          const matchingOption = options.find((opt) => String(opt.value) === String(rawVal));
          const nextVal = matchingOption ? matchingOption.value : (rawVal as unknown as T);
          if (controlledValue === undefined) {
            setInternalValue(nextVal);
          }
          onChange?.(nextVal);
        }}
        className={`${styles.trigger} ${radiusClass} ${sizeClass} ${
          isOpen ? styles.isOpen : ""
        } ${error ? styles.hasError : ""}`}
      >
        <div className={styles.contentLeft}>
          {selectedOption ? (
            <>
              {selectedOption.icon && (
                <span className={styles.itemIcon}>{selectedOption.icon}</span>
              )}
              <span className={styles.valueText}>{selectedOption.label}</span>
            </>
          ) : (
            <span className={styles.placeholderText}>{placeholder}</span>
          )}
        </div>

        <CaretDown
          size={14}
          weight="bold"
          className={`${styles.caretIcon} ${isOpen ? styles.caretIconOpen : ""}`}
          aria-hidden="true"
        />
      </button>

      {/* Floating Menu Panel */}
      {isOpen && (
        <div
          ref={listboxRef}
          id={listboxId}
          role="listbox"
          tabIndex={-1}
          aria-labelledby={label ? componentId : undefined}
          onKeyDown={handleMenuKeyDown}
          className={`${styles.menuPanel} ${radiusClass}`}
        >
          {options.map((option, index) => {
            const isSelected = option.value === activeValue;
            const isFocused = index === focusedIndex;

            return (
              <button
                key={String(option.value)}
                type="button"
                role="option"
                aria-selected={isSelected}
                disabled={option.disabled}
                onClick={() => handleSelect(option)}
                className={`${styles.menuItem} ${
                  isSelected ? styles.menuItemSelected : ""
                } ${isFocused ? styles.menuItemFocused : ""}`}
              >
                <div className={styles.menuItemLeft}>
                  {option.icon && (
                    <span className={styles.itemIcon}>{option.icon}</span>
                  )}
                  <span className={styles.menuItemLabel}>{option.label}</span>
                  {option.hint && (
                    <span className={styles.menuItemHint}>({option.hint})</span>
                  )}
                </div>

                {isSelected && (
                  <Check
                    size={14}
                    weight="bold"
                    className={styles.checkIcon}
                    aria-hidden="true"
                  />
                )}
              </button>
            );
          })}
        </div>
      )}

      {/* Error Message */}
      {error && <p className={styles.errorMessage}>{error}</p>}
    </div>
  );
}
