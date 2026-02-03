-- Point existing menu routes to real pages instead of placeholder.

update sys_menu set component = 'views/SystemUsers.vue' where path = '/system/users';
update sys_menu set component = 'views/SystemRoles.vue' where path = '/system/roles';

