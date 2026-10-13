const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const root = path.join(__dirname, '../../main/resources/static');
function fixture() {
  class Element {
    constructor(tag) { this.tag = tag; this.children = []; this.attrs = {}; this.scrollTop = 70; }
    set textContent(v) { this.text = String(v); this.children = []; }
    get textContent() { return (this.text || '') + this.children.map(c => c.textContent).join(''); }
    set innerHTML(_) { throw Error('Raw HTML forbidden'); }
    appendChild(c) { this.children.push(c); return c; }
    replaceChildren() { this.text = ''; this.children = []; }
    setAttribute(k,v) { this.attrs[k] = v; }
    scrollIntoView() { this.scrolled = true; }
    focus() { this.focused = true; }
  }
  const box = new Element('div'), counter = new Element('div');
  const flatten = e => [e, ...e.children.flatMap(flatten)];
  const c = vm.createContext({crypto:require('node:crypto').webcrypto, console:{error(){}},
    document:{createElement:t=>new Element(t), getElementById:id=>id==='quizInlineBox'?box:id==='quizCounter'?counter:flatten(box).find(e=>e.id===id)},
    documents:[{id:17,status:'READY',activeIndexVersion:'v1'}], selectedDocIds:[17], currentQuizId:9,
    currentQuizQuestions:[{id:21,question:'Test',options:['First','Second','Third']},{id:22,question:'Other',options:['Yes','No']}],
    quizUserAnswers:{}, inlineAttempt:null, inlineQuizResult:null, inlineSubmissionToken:null, inlineSubmittedAnswers:null, inlineQuizSubmitting:false,
    DocumentUI:{requireReady(){}}, DocumentFeatures:{}});
  c.window=c;
  vm.runInContext(fs.readFileSync(path.join(root,'js/quiz-interaction.js'),'utf8'),c);
  const html=fs.readFileSync(path.join(root,'viewer.html'),'utf8');
  vm.runInContext(html.slice(html.indexOf('    function renderQuizInline()'),html.indexOf('    // Chat Actions')),c);
  c.DocumentFeatures.startAttempt=async()=>({attemptId:55,questions:[
    {questionId:21,questionOrder:7,questionText:'Test',options:[{optionOrder:10,optionText:'First'},{optionOrder:20,optionText:'Second'},{optionOrder:30,optionText:'Third'}]},
    {questionId:22,questionOrder:8,questionText:'Other',options:[{optionOrder:3,optionText:'Yes'},{optionOrder:4,optionText:'No'}]}]});
  c.renderQuizInline();
  return {c,box,flatten,buttons:()=>flatten(box).filter(e=>e.tag==='button'),status:()=>c.document.getElementById('quizInteractionStatus')};
}
test('whole option card selects visibly, replaces same-question selection and keeps other answers/scroll',()=>{
  const f=fixture(); f.buttons()[0].onclick(); f.buttons()[2].onclick(); f.buttons()[3].onclick();
  assert.equal(f.c.quizUserAnswers[21],2); assert.equal(f.c.quizUserAnswers[22],0);
  assert.equal(f.buttons()[0].attrs['aria-pressed'],'false'); assert.equal(f.buttons()[2].attrs['aria-pressed'],'true');
  assert.match(f.buttons()[2].className,/is-selected/); assert.match(f.buttons()[2].textContent,/●/); assert.equal(f.box.scrollTop,70);
});
test('unanswered question focuses and no start/submit request is sent',async()=>{
  const f=fixture(); let calls=0; f.c.DocumentFeatures.startAttempt=async()=>{calls++;};
  f.buttons()[0].onclick(); await f.buttons().at(-1).onclick();
  assert.equal(calls,0); assert.match(f.status().textContent,/1 câu chưa chọn/);
  assert.equal(f.c.document.getElementById('quiz-question-22').focused,true);
});
test('submit uses actual option orders; server grades, reveals feedback only after success',async()=>{
  const f=fixture(); assert.doesNotMatch(f.box.textContent,/Đáp án đúng|Giải thích/);
  f.buttons()[2].onclick(); f.buttons()[3].onclick(); let submitted;
  f.c.DocumentFeatures.submitAttempt=async(a,answers,token)=>{
    submitted={a,answers,token}; return {score:50,totalQuestions:2,correctAnswers:1,perQuestionResults:[
      {questionOrder:7,isCorrect:false,correctOptionOrders:[10],explanation:'<img src=x onerror=alert(1)>'},
      {questionOrder:8,isCorrect:true,correctOptionOrders:[3],explanation:'Có căn cứ'}]};
  };
  await f.buttons().at(-1).onclick();
  assert.equal(submitted.answers[21],30); assert.equal(submitted.answers[22],3); assert.ok(submitted.token);
  assert.match(f.buttons()[0].className,/is-correct/); assert.match(f.buttons()[2].className,/is-wrong/);
  assert.match(f.box.textContent,/✕ SAI/); assert.match(f.box.textContent,/✓ ĐÚNG/);
  assert.match(f.box.textContent,/Giải thích: <img/); assert.match(f.box.textContent,/Điểm: 50 \/ 100/);
  assert.match(f.box.textContent,/Số câu sai: 1/); assert.equal(f.buttons()[0].disabled,true);
});
test('failed grading retry reuses attempt, token and answers and has useful safe error',async()=>{
  const f=fixture(); f.buttons()[0].onclick(); f.buttons()[3].onclick(); const calls=[];
  f.c.DocumentFeatures.submitAttempt=async(a,answers,token)=>{calls.push({a,answers,token});throw {status:502,message:'SQL stack secret'};};
  await f.buttons().at(-1).onclick(); assert.match(f.status().textContent,/Không thể chấm/); assert.doesNotMatch(f.box.textContent,/SQL/);
  const before=f.c.quizUserAnswers[21]; f.c.answerQuizQuestion(21,30); assert.equal(f.c.quizUserAnswers[21],before);
  await f.buttons().at(-1).onclick(); assert.equal(calls.length,2); assert.equal(calls[0].token,calls[1].token); assert.equal(calls[0].answers,calls[1].answers);
});
test('concurrent submission makes only one start and stale document result cannot grade current UI',async()=>{
  const f=fixture(); f.buttons()[0].onclick(); f.buttons()[3].onclick(); let release,starts=0,submits=0;
  const old=f.c.DocumentFeatures.startAttempt; f.c.DocumentFeatures.startAttempt=async()=>{starts++;await new Promise(r=>release=r);return old();};
  f.c.DocumentFeatures.submitAttempt=async()=>{submits++;};
  const a=f.buttons().at(-1).onclick(),b=f.buttons().at(-1).onclick(); f.c.selectedDocIds=[18]; release(); await Promise.all([a,b]);
  assert.equal(starts,1); assert.equal(submits,0); assert.equal(f.c.inlineQuizResult,null);
});
test('new quiz resets selection/result/token; repeated reads preserve current answers',async()=>{
  const f=fixture(); f.buttons()[0].onclick();
  const html=fs.readFileSync(path.join(root,'viewer.html'),'utf8');
  vm.runInContext(html.slice(html.indexOf('    async function loadDocumentQuiz('),html.indexOf('    async function retryDocumentProcessing(')),f.c);
  f.c.quizRequests=new Map(); f.c.isGenerationReady=()=>true;
  f.c.DocumentFeatures.quiz=async()=>({quizId:9,questions:f.c.currentQuizQuestions});
  await f.c.loadDocumentQuiz(17); assert.equal(f.c.quizUserAnswers[21],0);
  f.c.inlineQuizResult={}; f.c.inlineAttempt={}; f.c.inlineSubmissionToken='old'; f.c.inlineSubmittedAnswers={21:0};
  f.c.DocumentFeatures.quiz=async()=>({quizId:10,questions:[{id:31,question:'New quiz',options:['A','B']}]});
  await f.c.loadDocumentQuiz(17,true);
  assert.equal(f.c.currentQuizId,10); assert.equal(Object.keys(f.c.quizUserAnswers).length,0);
  assert.equal(f.c.inlineQuizResult,null); assert.equal(f.c.inlineAttempt,null);
  assert.equal(f.c.inlineSubmissionToken,null); assert.equal(f.c.inlineSubmittedAnswers,null);
});

test('preflight distinguishes missing quiz/questions/IDs/answer values; sends no request',async()=>{
  const scenarios = [
    [null, [{id:21,options:['A']}], {}, /Không xác định được bài/],
    [9, [], {}, /chưa có câu hỏi/],
    [9, [{options:['A']}], {undefined:0}, /Mã câu hỏi không hợp lệ/],
    [9, [{id:21,options:['A']}], {21:'A'}, /Lựa chọn đáp án không hợp lệ/],
    [9, [{id:21,options:['A']}], {}, /trả lời tất cả câu hỏi/]
  ];
  for (const [id,questions,answers,message] of scenarios) {
    const f=fixture(); f.c.currentQuizId=id; f.c.currentQuizQuestions=questions; f.c.quizUserAnswers=answers;
    let calls=0; f.c.DocumentFeatures.startAttempt=async()=>{calls++;};
    await f.c.submitQuizInline(f.buttons().at(-1),f.status());
    assert.equal(calls,0); assert.match(f.status().textContent,message);
  }
});

test('attempt start 400 identifies unpublished quiz separately from answer payload',async()=>{
  const f=fixture(); f.buttons()[0].onclick(); f.buttons()[3].onclick();
  f.c.DocumentFeatures.startAttempt=async()=>{throw {status:400,message:'Quiz is not PUBLISHED'};};
  await f.buttons().at(-1).onclick();
  assert.match(f.status().textContent,/bấm Tạo bài mới/);
  assert.doesNotMatch(f.status().textContent,/Dữ liệu câu trả lời chưa hợp lệ hoặc/);
});
