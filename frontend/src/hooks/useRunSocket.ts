import { useEffect, useMemo, useState } from "react"
import { Client, type IMessage } from "@stomp/stompjs"
import { useQueryClient } from "@tanstack/react-query"
import { TOKEN_KEY } from "@/api/client"
import { fetchRunLog } from "@/api/runs"
import type { RunEvent, RunStatus } from "@/types/run"

export type SocketState = "idle" | "connecting" | "live" | "reconnecting" | "error"

type LogEntry = [number, string]

function socketUrl(): string {
  const configured = import.meta.env.VITE_WS_URL as string | undefined
  if (configured) return configured
  const apiUrl = (import.meta.env.VITE_API_URL as string | undefined) ?? "http://localhost:8080"
  return apiUrl.replace(/^http/, "ws") + "/ws"
}

function parseEvent(body: string): RunEvent | null {
  try {
    return JSON.parse(body) as RunEvent
  } catch {
    return null
  }
}

// Lines are keyed by their offset, so the backlog and live messages can overlap safely.
function merge(previous: Map<number, string>, entries: LogEntry[]): Map<number, string> {
  const next = new Map(previous)
  for (const [offset, line] of entries) next.set(offset, line)
  return next
}

export function useRunSocket(runId: number, enabled: boolean) {
  const queryClient = useQueryClient()
  const [logMap, setLogMap] = useState<Map<number, string>>(() => new Map())
  const [status, setStatus] = useState<RunStatus | null>(null)
  const [connection, setConnection] = useState<SocketState>("connecting")
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    const loadBacklog = () =>
      fetchRunLog(runId, 0)
        .then((chunk) => {
          if (cancelled) return
          const start = chunk.next - chunk.lines.length
          const entries = chunk.lines.map((line, index): LogEntry => [start + index, line])
          setLogMap((previous) => merge(previous, entries))
        })
        .catch(() => undefined)

    void loadBacklog()

    const token = localStorage.getItem(TOKEN_KEY)
    if (!enabled || !token) {
      return () => {
        cancelled = true
      }
    }

    const client = new Client({
      brokerURL: socketUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,
    })

    client.onConnect = () => {
      setConnection("live")
      setError(null)
      client.subscribe(`/topic/runs/${runId}`, (message: IMessage) => {
        const event = parseEvent(message.body)
        if (!event) return
        if (event.type === "LOG") {
          const { offset, line } = event
          setLogMap((previous) => merge(previous, [[offset, line]]))
        } else {
          setStatus(event.status)
          void queryClient.invalidateQueries({ queryKey: ["run", runId] })
        }
      })
      // Load the backlog after subscribing so no line can fall into the gap.
      void loadBacklog()
    }

    client.onWebSocketClose = () => {
      setConnection((current) => (current === "error" ? current : "reconnecting"))
    }

    client.onStompError = (frame) => {
      setConnection("error")
      setError(frame.headers["message"] ?? "WebSocket error")
      void client.deactivate()
    }

    client.activate()

    return () => {
      cancelled = true
      void client.deactivate()
    }
  }, [runId, enabled, queryClient])

  const lines = useMemo(
    () => [...logMap.entries()].sort((a, b) => a[0] - b[0]).map(([, line]) => line),
    [logMap],
  )

  return { lines, status, connection: enabled ? connection : ("idle" as SocketState), error }
}