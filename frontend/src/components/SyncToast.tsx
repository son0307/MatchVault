import { useEffect, useRef } from "react";
import { X } from "lucide-react";

type SyncToastProps = {
  message: string;
  type: "success" | "error";
  onClose: () => void;
};

export function SyncToast({ message, type, onClose }: SyncToastProps) {
  const onCloseRef = useRef(onClose);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    const timerId = window.setTimeout(() => onCloseRef.current(), 4000);
    return () => window.clearTimeout(timerId);
  }, [message, type]);

  return (
    <div
      className={`sync-toast ${type}`}
      role={type === "error" ? "alert" : "status"}
      aria-live={type === "error" ? "assertive" : "polite"}
    >
      <span>{message}</span>
      <button type="button" aria-label="알림 닫기" onClick={onClose}>
        <X size={18} aria-hidden="true" />
      </button>
    </div>
  );
}
