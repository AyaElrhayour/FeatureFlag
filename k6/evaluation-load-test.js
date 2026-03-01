import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
const errorRate = new Rate('evaluation_errors');
const evaluationDuration = new Trend('evaluation_duration', true);

export const options = {
    scenarios: {
        baseline: {
            executor: 'constant-vus',
            vus: 1,
            duration: '15s',
            tags: { scenario: 'baseline' },
            startTime: '0s',
        },
        load: {
            executor: 'ramping-vus',
            startTime: '20s',
            stages: [
                { duration: '10s', target: 50 },
                { duration: '30s', target: 50 },
                { duration: '10s', target: 0  },
            ],
            tags: { scenario: 'load' },
        },

        stress: {
            executor: 'ramping-vus',
            startTime: '75s',
            stages: [
                { duration: '10s', target: 100 },
                { duration: '20s', target: 200 },
                { duration: '10s', target: 0   },
            ],
            tags: { scenario: 'stress' },
        },
    },

    thresholds: {
        'http_req_duration{scenario:baseline}': ['p(95)<100'],
        'http_req_duration{scenario:load}':     ['p(95)<200'],
        'http_req_duration{scenario:stress}':   ['p(95)<500'],
        'http_req_failed{scenario:baseline}': ['rate<0.01'],
        'http_req_failed{scenario:load}':     ['rate<0.01'],
        'http_req_failed{scenario:stress}':   ['rate<0.10'],
        'evaluation_errors': ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const FLAG_KEY  = 'perf-test-flag';

export function setup() {
    console.log(`Setting up performance test against ${BASE_URL}`);
    const createResponse = http.post(
        `${BASE_URL}/api/v1/flags`,
        JSON.stringify({
            flagKey:      FLAG_KEY,
            description:  'Performance test flag — created by k6',
            environments: { dev: true, staging: true, prod: true },
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-Actor':      'k6-performance-test',
            },
        }
    );
    if (createResponse.status !== 201 && createResponse.status !== 409) {
        console.error(`Failed to create test flag: ${createResponse.status} ${createResponse.body}`);
    } else {
        console.log(`Test flag '${FLAG_KEY}' ready`);
    }

    return { flagKey: FLAG_KEY };
}

export default function (data) {
    const environments = ['dev', 'staging', 'prod'];
    const environment  = environments[Math.floor(Math.random() * environments.length)];
    const userId       = `user-${Math.floor(Math.random() * 1000)}`;

    const startTime = Date.now();

    const response = http.post(
        `${BASE_URL}/api/v1/flags/${data.flagKey}/evaluate`,
        JSON.stringify({
            environment: environment,
            context: {
                userId:     userId,
                attributes: { region: 'us-east', plan: 'premium' },
            },
        }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags:    { endpoint: 'evaluate' },
        }
    );

    const duration = Date.now() - startTime;
    evaluationDuration.add(duration);
    const success = check(response, {
        'status is 200':           (r) => r.status === 200,
        'response has flagKey':    (r) => r.json('flagKey')    === data.flagKey,
        'response has enabled':    (r) => r.json('enabled')    !== undefined,
        'response has flagVersion':(r) => r.json('flagVersion') !== undefined,
        'response has reason':     (r) => r.json('reason')     !== null,
        'response has environment':(r) => r.json('environment') === environment,
    });

    errorRate.add(!success);
    sleep(Math.random() * 0.04 + 0.01);
}

export function teardown(data) {
    console.log('Tearing down — deleting test flag');

    const deleteResponse = http.del(
        `${BASE_URL}/api/v1/flags/${data.flagKey}`,
        null,
        {
            headers: { 'X-Actor': 'k6-performance-test' },
        }
    );

    if (deleteResponse.status === 204) {
        console.log(`Test flag '${data.flagKey}' deleted successfully`);
    } else {
        console.warn(`Failed to delete test flag: ${deleteResponse.status}`);
    }
}