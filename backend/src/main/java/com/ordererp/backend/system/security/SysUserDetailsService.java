package com.ordererp.backend.system.security;

import com.ordererp.backend.system.entity.SysUser;
import com.ordererp.backend.system.repository.SysMenuRepository;
import com.ordererp.backend.system.repository.SysUserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class SysUserDetailsService implements UserDetailsService {
    private final SysUserRepository userRepository;
    private final SysMenuRepository menuRepository;

    public SysUserDetailsService(SysUserRepository userRepository, SysMenuRepository menuRepository) {
        this.userRepository = userRepository;
        this.menuRepository = menuRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userRepository.findByUsernameAndDeleted(username, 0)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        boolean enabled = user.getStatus() != null && user.getStatus() == 1;
        // Compatible with legacy seed data (e.g. erp_data.sql) where password might be stored as plain "123456".
        // Spring Security's DelegatingPasswordEncoder expects an id prefix like "{noop}" / "{bcrypt}".
        String storedPassword = user.getPassword();
        if (storedPassword != null && !storedPassword.startsWith("{")) {
            storedPassword = "{noop}" + storedPassword;
        }
        List<SimpleGrantedAuthority> authorities = menuRepository.findPermsByUserId(user.getId()).stream()
                .filter(perm -> perm != null && !perm.isBlank())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return new SysUserDetails(user.getId(), user.getUsername(), storedPassword, user.getNickname(), enabled,
                authorities);
    }
}
