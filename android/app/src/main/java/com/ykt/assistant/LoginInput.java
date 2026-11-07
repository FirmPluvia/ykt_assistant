package com.ykt.assistant;

/** Login values remain in native memory; do not trim passwords or persist credentials. */
final class LoginInput {
    private LoginInput() {}
    static String phone(String raw) { String v=raw==null?"":raw.trim(); if(!v.matches("1[0-9]{10}")) throw new IllegalArgumentException("请输入 11 位手机号"); return v; }
    static String account(String raw) {
        String v=raw==null?"":raw.trim(); if(!v.contains("@")) return phone(v);
        if(v.length()>254||!v.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("请输入有效邮箱地址"); return v;
    }
    static String password(String value) { if(value==null||value.isEmpty()||value.length()>1024) throw new IllegalArgumentException("请输入密码，最长 1024 个字符"); return value; }
    static String code(String raw) { String v=raw==null?"":raw.trim(); if(!v.matches("[0-9]{4,8}")) throw new IllegalArgumentException("请输入 4 至 8 位短信验证码"); return v; }
    static void proof(String ticket,String rand) { if(ticket==null||rand==null||ticket.isEmpty()||rand.isEmpty()||ticket.length()>8192||rand.length()>1024) throw new IllegalArgumentException("请先完成官方安全验证"); }
}
