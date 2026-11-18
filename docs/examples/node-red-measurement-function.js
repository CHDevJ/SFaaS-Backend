// Node-RED Function node: configure STF_INGESTION_URL and STF_INTERNAL_API_KEY
// on the Node-RED runtime host, not in a browser/frontend environment.
// Input msg.payload must contain the actual measurements below.
const url = env.get('STF_INGESTION_URL');
const apiKey = env.get('STF_INTERNAL_API_KEY');
if (!url || !apiKey) {
    node.error('Set STF_INGESTION_URL and STF_INTERNAL_API_KEY on the Node-RED runtime.');
    return null;
}
const input = msg.payload;
if (!input || !input.equipmentCode || !Array.isArray(input.values) || !input.values.length) {
    node.error('Expected equipmentCode and a non-empty values array.');
    return null;
}
const sequence = (context.get('stfBatchSequence') || 0) + 1;
context.set('stfBatchSequence', sequence);
msg.method = 'POST';
msg.url = url;
msg.headers = { 'Content-Type': 'application/json', 'X-Internal-Api-Key': apiKey };
msg.payload = {
    // Generate once for a new batch. A retry must retain this entire payload.
    messageId: input.messageId || `nr-${Date.now()}-${node.id}-${sequence}`,
    equipmentCode: input.equipmentCode,
    measuredAt: input.measuredAt || new Date().toISOString(),
    values: input.values,
};
return msg;
