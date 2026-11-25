import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
// This suite seeds data and rules only in the local demo emulators.
assert.equal(process.env.GCLOUD_PROJECT, 'demo-libri', 'Use emulators:exec --project demo-libri');
for (const variable of ['FIREBASE_AUTH_EMULATOR_HOST', 'FIREBASE_DATABASE_EMULATOR_HOST', 'FIRESTORE_EMULATOR_HOST']) {
  assert.match(process.env[variable] ?? '', /^(127\.0\.0\.1|localhost):\d+$/, variable);
}
const ns = 'demo-libri';
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const databaseHost = process.env.FIREBASE_DATABASE_EMULATOR_HOST;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
async function user() {
  const r = await fetch('http://'+authHost+'/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key', {
    method:'POST', headers:{'content-type':'application/json'}, body:JSON.stringify({returnSecureToken:true})
  });
  assert.ok(r.ok, await r.clone().text());
  return r.json();
}
async function verify(url, method, token, data, allow) {
  const r = await fetch(url, {method, headers:{'content-type':'application/json', ...(token && {authorization:'Bearer '+token})},
    ...(data !== undefined && {body:JSON.stringify(data)})});
  assert.equal(r.ok, allow, method+' '+new URL(url).pathname+': '+await r.text());
}
function databaseUrl(path, token) {
  const url = new URL('http://' + databaseHost + '/' + path + '.json');
  url.searchParams.set('ns', ns);
  if (token && token !== 'owner') url.searchParams.set('auth', token);
  return url;
}
const firestoreUrl = 'http://' + firestoreHost + '/v1/projects/demo-libri/databases/(default)/documents';
const db = (path, method, token, data, allow) => verify(databaseUrl(path, token), method, token === 'owner' ? token : null, data, allow);
const fs = (path, method, token, data, allow) => verify(firestoreUrl + '/' + path, method, token, data, allow);
const [a,b,unknown] = await Promise.all([user(), user(),user()]);
await db('.settings/rules','PUT','owner',JSON.parse(readFileSync('firebase/database.rules.json')),true);
for (const [u,id] of [[a,'temi_L'],[b,'temi_1']]) {
  await db('robotAccess/'+u.localId,'PUT','owner',id,true);
  await fs('robotAccess/'+u.localId,'PATCH','owner',{fields:{robotId:{stringValue:id}}},true);
}
const timestamp = {'.sv':'timestamp'};
const presence = {floor:'L',online:true,busy:false,currentService:'idle',updatedAt:timestamp};
await db('robotAccess/'+a.localId,'GET',a.idToken,undefined,true);
await db('robotAccess/'+a.localId,'PUT',a.idToken,'temi_1',false);
await db('robots/temi_L','PUT',a.idToken,presence,true);
await db('robots/temi_L','PUT',unknown.idToken,presence,false);
await db('robots/temi_L','PUT',b.idToken,presence,false);
const roomUrl = (u) => databaseUrl('rooms/demo', u.idToken);
const reader = await fetch(roomUrl(a), {headers:{'X-Firebase-ETag':'true'}});
assert.ok(reader.ok);
const etag = reader.headers.get('etag');
assert.ok(etag);
const aSession = 'a'.repeat(32), bSession = 'b'.repeat(32);
const responses = await Promise.all([[a,aSession],[b,bSession]].map(([u,id]) =>
  fetch(roomUrl(u), {method:'PUT',headers:{'if-match':etag,'content-type':'application/json'},
    body:JSON.stringify({players:{A:{[id]:true}},attacker:'A',score:{A:0,B:0}})})));
assert.deepEqual(responses.map(r=>r.status).sort((x,y)=>x-y),[200,412]);
const winner = responses[0].ok ? aSession : bSession;
const loser = responses[0].ok ? bSession : aSession;
await db('rooms/demo/players/A/'+loser,'DELETE',b.idToken,undefined,true);
const room = await (await fetch(roomUrl(a))).json();
assert.deepEqual(room.players.A,{[winner]:true});
await db('rooms/demo/players/B/'+loser,'PUT',b.idToken,true,true);
const round = {id:1,attacker:'A',dir:'LEFT',startAt:123};
await db('rooms/demo/round','PUT',a.idToken,round,true);
await db('rooms/demo/players/B/'+loser,'DELETE',b.idToken,undefined,true);
await db('rooms/demo/round/result','PUT',a.idToken,'HIT',false);
await db('rooms/demo/round','DELETE',a.idToken,undefined,true);
await db('rooms/demo/players/A/'+winner,'DELETE',a.idToken,undefined,true);
await db('rooms/demo','GET',unknown.idToken,undefined,false);
await db('omok/rooms/demo/seats/A','PUT',a.idToken,{id:'player',name:'테스트'},true);
await db('omok/rooms/demo','GET',unknown.idToken,undefined,false);
const pair = 'requests/pair_temi_L_temi_1';
await db(pair,'PUT',a.idToken,{fromRobot:'temi_L',toRobot:'temi_1',roomCode:'invite'+'a'.repeat(32),type:'chamchamcham',status:'pending',createdAt:timestamp},true);
await db(pair,'PATCH',a.idToken,{status:'accepted',respondedAt:timestamp},false);
await db(pair,'PATCH',b.idToken,{status:'accepted',respondedAt:timestamp},true);
await db(pair,'PATCH',b.idToken,{status:'rejected',respondedAt:timestamp},false);
const pending = {fromRobot:'temi_L',toRobot:'temi_1',roomCode:'invite'+'b'.repeat(32),
  type:'chamchamcham',status:'pending',createdAt:Date.now()-61_000};
await db(pair,'PUT','owner',pending,true);
await db(pair,'PATCH',b.idToken,{status:'accepted',respondedAt:timestamp},false);
await db(pair,'PUT',a.idToken,{...pending,createdAt:timestamp},true);
await db(pair,'PATCH',b.idToken,{status:'cancelled',respondedAt:timestamp},false);
await db(pair,'PATCH',a.idToken,{status:'cancelled',respondedAt:timestamp},true);
const fields = { rating:{integerValue:'4'}, service:{stringValue:'eye_work'}, robotId:{stringValue:'temi_L'}, floor:{stringValue:'L'} };
function survey(token, id, data, allow) {
  return verify(firestoreUrl + ':commit','POST',token,{
    writes:[{update:{name:'projects/demo-libri/databases/(default)/documents/feedback/'+id,fields:data},
      updateTransforms:[{fieldPath:'createdAt',setToServerValue:'REQUEST_TIME'}],currentDocument:{exists:false}}]
  },allow);
}
await survey(a.idToken,'valid',fields,true);
await survey(unknown.idToken,'unknown',fields,false);
await survey(b.idToken,'wrong-floor',fields,false);
await survey(a.idToken,'bad-rating',{...fields,rating:{integerValue:'9'}},false);
await survey(a.idToken,'extra-field',{...fields,note:{stringValue:'unexpected'}},false);
await fs('feedback/valid','GET',a.idToken,undefined,false);
await fs('omok_ranking/n_test','GET',unknown.idToken,undefined,false);
console.log('Firebase rules checks passed: seats, rounds, requests, and feedback');
