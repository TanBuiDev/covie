export type BackendFieldErrors = Record<string, string[]>;

export type SetFieldError<FieldName extends string> = (
  field: FieldName,
  error: { type: "server"; message: string },
) => void;

const FIELD_CODE_COPY: Record<string, string> = {
  REQUIRED: "Trường này là bắt buộc.",
  INVALID: "Giá trị không hợp lệ.",
  TOO_SHORT: "Giá trị quá ngắn.",
  TOO_LONG: "Giá trị quá dài.",
  OUT_OF_RANGE: "Giá trị nằm ngoài phạm vi cho phép.",
};

function codeCopy(code: string): string {
  return FIELD_CODE_COPY[code] ?? "Giá trị chưa hợp lệ. Vui lòng kiểm tra lại.";
}

/**
 * Maps stable backend field names/codes to form fields without inspecting a human message.
 * Unknown backend fields are returned for a form-level error surface.
 */
export function applyFieldErrors<FieldName extends string>(
  fields: BackendFieldErrors | undefined,
  fieldMap: Readonly<Record<string, FieldName>>,
  setError: SetFieldError<FieldName>,
): string[] {
  if (!fields) return [];

  const formErrors: string[] = [];
  for (const [backendField, codes] of Object.entries(fields)) {
    const field = fieldMap[backendField];
    if (!field) {
      for (const code of codes) formErrors.push(`${backendField}: ${code}`);
      continue;
    }

    const firstCode = codes[0] ?? "INVALID";
    setError(field, { type: "server", message: codeCopy(firstCode) });
  }
  return formErrors;
}
