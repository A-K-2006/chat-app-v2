// Relay load test — k6 does not speak STOMP, so we hand-roll the minimal STOMP text
// frames (CONNECT / SUBSCRIBE / SEND / MESSAGE) over k6's raw WebSocket API, through the
// Spring Cloud Gateway (single entry point). The gateway round-robins WS connections across
// chat-service replicas, so load spreads across instances.
//
// Register ONCE per VU (a user registers once and holds a connection), not per iteration —
// re-registering every iteration makes the load generator's BCrypt hashing the CPU bottleneck.
//
// Run (metrics stream to Prometheus -> Grafana "Relay — k6 Load Test" dashboard):
//   K6_PROMETHEUS_RW_SERVER_URL=http://localhost:19090/api/v1/write \
//     k6 run --out experimental-prometheus-rw loadtest/relay-load.js
import ws from 'k6/ws';
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const deliveryLatency = new Trend('delivery_latency_ms', true);
const messagesSent = new Counter('messages_sent');
const wsErrors = new Rate('ws_errors');
const connectFailures = new Counter('connect_failures');

const GW = __ENV.GW || 'http://localhost:18080';
const WS_GW = __ENV.WS_GW || 'ws://localhost:18080';

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 50 },
        { duration: '15s', target: 100 },
        { duration: '20s', target: 150 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    ws_errors: ['rate<0.05'],
    delivery_latency_ms: ['p(95)<2000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// One shared peer user so each VU can open a 1:1 and receive its own echo.
export function setup() {
  const username = `loadpeer_${Date.now()}`;
  const reg = http.post(
    `${GW}/auth/register`,
    JSON.stringify({ username, email: `${username}@load.test`, password: 'password123' }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '20s' }
  );
  if (reg.status !== 201) throw new Error(`setup register failed: ${reg.status}`);
  return { peerId: reg.json('userId') };
}

let vuToken = null;
let vuConvId = null;

export default function (data) {
  if (!vuToken) {
    const username = `load${__VU}_${Date.now()}`;
    const reg = http.post(
      `${GW}/auth/register`,
      JSON.stringify({ username, email: `${username}@load.test`, password: 'password123' }),
      { headers: { 'Content-Type': 'application/json' }, timeout: '20s' }
    );
    if (reg.status !== 201) {
      connectFailures.add(1);
      wsErrors.add(1);
      return;
    }
    vuToken = reg.json('accessToken');
    const conv = http.post(
      `${GW}/conversations/direct`,
      JSON.stringify({ peerId: data.peerId }),
      { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${vuToken}` }, timeout: '20s' }
    );
    if (conv.status !== 201) {
      connectFailures.add(1);
      wsErrors.add(1);
      return;
    }
    vuConvId = conv.json('id');
  }
  const token = vuToken;
  const convId = vuConvId;
  const sendTs = {};

  const res = ws.connect(`${WS_GW}/ws?access_token=${token}`, {}, function (socket) {
    socket.on('open', () => {
      socket.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0');
    });

    socket.on('message', (msg) => {
      const frames = String(msg).split('\0').filter((f) => f.trim().length > 0);
      for (const frame of frames) {
        const sep = frame.indexOf('\n\n');
        const head = sep >= 0 ? frame.slice(0, sep) : frame;
        const body = sep >= 0 ? frame.slice(sep + 2) : '';
        const cmd = head.split('\n')[0];

        if (cmd === 'CONNECTED') {
          socket.send('SUBSCRIBE\nid:sub-0\ndestination:/user/queue/messages\n\n\0');
          socket.setInterval(() => {
            const cmid = uuidv4();
            sendTs[cmid] = Date.now();
            const payload = JSON.stringify({ conversationId: convId, messageId: cmid, body: 'load' });
            socket.send(
              `SEND\ndestination:/app/chat.send\ncontent-type:application/json\n\n${payload}\0`
            );
            messagesSent.add(1);
          }, 2000);
        } else if (cmd === 'MESSAGE') {
          try {
            const m = JSON.parse(body);
            const ts = sendTs[m.messageId];
            if (ts) {
              deliveryLatency.add(Date.now() - ts);
              delete sendTs[m.messageId];
            }
          } catch (e) {
            wsErrors.add(1);
          }
        } else if (cmd === 'ERROR') {
          wsErrors.add(1);
        }
      }
    });

    socket.on('error', () => wsErrors.add(1));
    socket.setTimeout(() => socket.close(), 25000);
  });

  check(res, { 'ws upgrade 101': (r) => r && r.status === 101 });
}
