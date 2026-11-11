"""Update synthetic billing fixtures in the dedicated local QA database only."""
from pathlib import Path
import datetime,os,re,shutil,subprocess,sys
mode,username=sys.argv[1:3]
if mode not in ('usage','expire') or not re.fullmatch(r'billing_browser_\d+',username):
    raise SystemExit('Only synthetic billing_browser_<timestamp> QA users are allowed')
props=dict(line.split('=',1) for line in Path('src/main/resources/application.properties').read_text(encoding='utf-8').splitlines() if '=' in line and not line.lstrip().startswith('#'))
def resolve(value):
    match=re.fullmatch(r'\$\{([^:}]+):?([^}]*)\}',value)
    return os.environ.get(match[1],match[2]) if match else value
username_db=os.environ.get('TEST_DB_USERNAME',resolve(props.get('spring.datasource.username','root')))
password=os.environ.get('TEST_DB_PASSWORD',resolve(props.get('spring.datasource.password','')))
client=os.environ.get('MYSQL_CLIENT_PATH') or shutil.which('mysql') or 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
if mode=='usage':
    feature,count=sys.argv[3],int(sys.argv[4])
    if feature not in ('CHAT','QUIZ','FLASHCARD','UPLOAD') or not 0<=count<=1000000:raise SystemExit('Invalid QA quota')
    period=(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=7)).date().replace(day=1).isoformat()
    sql="INSERT INTO monthly_feature_usage(user_id,period_start,feature,used) SELECT id,'"+period+"','"+feature+"',"+str(count)+" FROM users WHERE username='"+username+"' ON DUPLICATE KEY UPDATE used="+str(count)+";"
else:
    sql="UPDATE subscriptions s JOIN users u ON s.user_id=u.id SET s.end_date=DATE_SUB(NOW(),INTERVAL 1 DAY),s.status='ACTIVE' WHERE u.username='"+username+"';"
env=os.environ.copy();env['MYSQL_PWD']=password
result=subprocess.run([client,'--host=127.0.0.1','--connect-timeout=5','--user='+username_db,'studyassistant_browser_test','-e',sql],env=env,capture_output=True,text=True,timeout=20)
if result.returncode:print('QA fixture update failed:',result.stderr)
sys.exit(result.returncode)
