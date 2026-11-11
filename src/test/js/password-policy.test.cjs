const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const context=vm.createContext({window:{},TextEncoder,document:{getElementById:()=>null}});
vm.runInContext(fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/auth-recovery.js'),'utf8'),context);
for(const password of ['short1!','alllowercase1!','ALLUPPERCASE1!','NoDigitsHere!','NoSpecial123','Ab1!'+ 'á'.repeat(35),'12345678','        ']){
 test('password policy rejects missing requirements: '+password.length+' characters',()=>assert.equal(context.window.PasswordRules.valid(password),false));
}
for(const password of ['Password123!','MậtKhẩu123!','Ab1!xxxx']){
 test('password policy accepts complete requirements: '+password.length+' characters',()=>assert.equal(context.window.PasswordRules.valid(password),true));
}
