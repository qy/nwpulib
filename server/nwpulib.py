# -*- coding:utf8 -*-
import urllib
import urllib2
from lxml import etree
from BaseHTTPServer import *
import json
import zlib
import binascii
import cStringIO;
import gzip
import Queue
import threading

hosts = []

queue = Queue.Queue()

class ThreadUrl(threading.Thread):
    """Threaded Url Grab"""
    def __init__(self, queue):
        threading.Thread.__init__(self)
        self.queue = queue

    def run(self):
        while True:
            #grabs host from queue
            book = self.queue.get()
            url = 'http://202.117.255.187:8080/opac/ajax_item.php?marc_no=' + book['marc_no']

            try:
                #grabs urls of hosts and prints first 1024 bytes of page
                print "fetching %s ..." % url 
                f = urllib2.urlopen(url, timeout=15)
                html = ''.join(f.readlines())
                tree = etree.HTML(html)
                trs = tree.xpath("/descendant::tr[@class='whitetext']")
                borrow_info = []
                for tr in trs:
                    where = tr[3].attrib['title']
                    where = eval(repr(where).replace('u',''))
                    if len(tr[4].getchildren()) > 0:
                        status = tr[4][0].text
                    else:
                        status = tr[4].text
                    status = eval(repr(status).replace('u',''))
                    borrow_info.append({"where":where, "status":status})
                book['borrow_info'] = borrow_info
            except urllib2.URLError,urllib2.HTTPError:
                self.queue.task_done()  
                continue
            except socket.error:
                time.sleep(1)
                self.queue.put(host)
                self.queue.task_done()  
                continue  

            #signals to queue job is done
            self.queue.task_done()

#spawn a pool of threads, and pass them queue instance 
for i in range(10):
    t = ThreadUrl(queue)
    t.setDaemon(True)
    t.start()

def fill_borrow_info(booklist):
    #populate queue with data
    for book in booklist:
        queue.put(book)
    
    #wait on the queue until everything has been processed
    queue.join()
    print "All URL fetched"

def get_book_list(title, page):
    url = 'http://202.117.255.187:8080/opac/openlink.php?dept=ALL&title=%s&doctype=ALL&lang_code=ALL&match_flag=forward&displaypg=10&showmode=list&orderby=DESC&sort=CATA_DATE&onlylendable=no&with_ebook=&page=%s' % (title, page)
    print url
    f = urllib2.urlopen(url)
    html = ''.join(f.readlines())
    tree = etree.HTML(html)
    lis = tree.xpath("/descendant::li[@class='book_list_info']")
    booklist = []
    for li in lis:
        a = li.xpath("h3/a")[0]
        br1 = li.xpath("p/br")[0]
        br2 = li.xpath("p/span/br")[0]
        span = li.xpath("p/span")[0]

        title = a.text.strip().encode("utf8")
        call_no = a.tail.strip()
        marc_no = a.attrib['href'].replace('item.php?marc_no=', '')
        author = span.tail.strip().encode("utf8")
        nr_copy = span.text.strip().encode("utf8").replace('馆藏复本：', '')
        nr_borrowable = br2.tail.strip().encode("utf8").replace('可借复本：', '')
        publisher = br1.tail.strip().encode("utf8")

        book = {"marc_no"   : marc_no,
                "title"     : title,
                "call_no"   : call_no,
                "author"    : author,
                "nr_copy"    : nr_copy,
                "nr_borrowable": nr_borrowable,
                "publisher" : publisher,
                "borrow_info": "获取失败"
                }
        booklist.append(book)
    
    fill_borrow_info(booklist)

    tp = tree.xpath('//*[@id="num"]/span/b/font[2]')
    if len(tp) > 0:
        total_pages = tp[0].text
    else:
        total_pages = '1'
    return {"books":booklist,
            "total_pages":total_pages,
            "current_page":page,
            }

class NwpulibHTTPHandler(BaseHTTPRequestHandler):

    def _writeheaders(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()
    def do_HEAD(self):
        self._writeheaders();
    def do_GET(self):
        # path like '/s/title/1'
        cmd = self.path.strip('/').split('/')
        if len(cmd) == 3 and cmd[0] == 's':
            title = cmd[1]
            page = cmd[2]
            content = get_book_list(title, page)
            jsn = json.dumps(content, ensure_ascii=False, indent=4)
#            print jsn
#            compressed_json = zlib.compress(jsn, zlib.Z_DEFAULT_COMPRESSION)
#            s = binascii.hexlify(compressed_json)
#            print "Content size=%d, compressed size=%d, hexlify size=%d" % (len(jsn), len(compressed_json), len(s))

            buf = cStringIO.StringIO()
            output = gzip.GzipFile(mode='wb', fileobj=buf)
            output.write(jsn)
            output.close()
            buf.seek(0)
            compressed_json = buf.getvalue()

            print "Content size=%d, compressed size=%d" % (len(jsn), len(compressed_json))

            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.send_header("Content-Length", str(len(compressed_json))) 
            self.send_header("Content-Encoding", "gzip")
            self.end_headers()
            self.wfile.write(compressed_json)
            self.wfile.flush()

server = HTTPServer(("", 8000), NwpulibHTTPHandler)
server.serve_forever()
