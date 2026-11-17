interface FormFieldProps {
  id: string;
  label: string;
  type: 'email' | 'password' | 'text' | 'date';
  value: string;
  onChange: (value: string) => void;
  autoComplete?: string;
  error?: string;
  hint?: string;
  disabled?: boolean;
  placeholder?: string;
  inputMode?: 'text' | 'decimal';
  maxLength?: number;
}

export function FormField({
  id,
  label,
  type,
  value,
  onChange,
  autoComplete,
  error,
  hint,
  disabled,
  placeholder,
  inputMode,
  maxLength,
}: FormFieldProps) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        name={id}
        type={type}
        value={value}
        autoComplete={autoComplete}
        disabled={disabled}
        placeholder={placeholder}
        inputMode={inputMode}
        maxLength={maxLength}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        onChange={(event) => onChange(event.target.value)}
      />
      {error ? (
        <p id={`${id}-error`} className="field-error" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="muted small">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
