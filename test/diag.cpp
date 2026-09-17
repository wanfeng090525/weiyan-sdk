#include <iostream>
#include <string>
#include <cstring>
#include <unistd.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <fcntl.h>
#include <errno.h>
#include <poll.h>
#include "../android/jni/weiyan/wy_util.h"

using namespace std;

int main() {
    cout << "== getaddrinfo ==" << endl;
    struct addrinfo hints, *res = NULL;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;
    int rc = getaddrinfo("wy.llua.cn", NULL, &hints, &res);
    cout << "getaddrinfo rc=" << rc << (rc != 0 ? gai_strerror(rc) : "") << endl;
    if (res) {
        char ip[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &((struct sockaddr_in *)res->ai_addr)->sin_addr, ip, sizeof(ip));
        cout << "ip=" << ip << endl;
        freeaddrinfo(res);
    }

    cout << "\n== aliyun dns ==" << endl;
    string ip2 = wy_getip("wy.llua.cn");
    cout << "wy_getip=" << ip2 << endl;

    cout << "\n== connect 80 ==" << endl;
    int fd = (int)socket(AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons(80);
    inet_pton(AF_INET, ip2.c_str(), &sa.sin_addr);
    // non-blocking connect with timeout
    int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    int c = connect(fd, (struct sockaddr *)&sa, sizeof(sa));
    cout << "connect ret=" << c << " errno=" << errno << endl;
    struct pollfd pfd = {fd, POLLOUT, 0};
    int pr = poll(&pfd, 1, 5000);
    cout << "poll ret=" << pr << endl;
    if (pr > 0) {
        int soerr = 0; socklen_t sl = sizeof(soerr);
        getsockopt(fd, SOL_SOCKET, SO_ERROR, &soerr, &sl);
        cout << "so_error=" << soerr << endl;
    }
    fcntl(fd, F_SETFL, flags);
    if (pr > 0) {
        string req = "GET / HTTP/1.1\r\nHost: wy.llua.cn\r\nConnection: close\r\n\r\n";
        send(fd, req.c_str(), req.size(), 0);
        char buf[512];
        int n = (int)read(fd, buf, sizeof(buf)-1);
        cout << "read n=" << n << (n > 0 ? string(buf, n) : "") << endl;
    }
    close(fd);
    return 0;
}
